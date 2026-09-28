#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OpenFlux Mobile Node Deployer
Автономный мобильный веб-сервис для развертывания ноды OpenFlux.
Оптимизирован для экранов мобильных телефонов (Android, Termux, мобильный браузер).
Без использования смайликов.
"""

import os
import sys
import json
import base64
import zlib
import re
import secrets
from http.server import HTTPServer, BaseHTTPRequestHandler
import urllib.parse
import subprocess

PINNED_SCRIPT_URL = "https://raw.githubusercontent.com/p1neappleXpress/OpenFlux/38a65e5ab7c5d959dc0a4a2e4f3f7501d0e4fc81/deploy/node-install.sh"
PINNED_SHA256 = "48f2adb0b80701795180bed1ef33f5c916603f07957c1df41bdc917eeb630efa"


def normalize_yandex_url(url):
    clean = url.strip()
    if "#" in clean:
        clean = clean.split("#")[0]
    if "?" in clean:
        clean = clean.split("?")[0]
    clean = clean.rstrip("/")
    match = re.search(r'https?://(?:docs|disk)\.yandex\.(?:ru|com|by|kz|uz)/edit/d/([A-Za-z0-9_-]{16,200})', clean)
    if match:
        return f"https://docs.yandex.ru/edit/d/{match.group(1)}"
    return clean


def generate_share_link(name, doc_url, key, host, port):
    cfg = {
        "name": name or "MyOpenFluxNode",
        "negotiate": True,
        "secret": key,
        "context": doc_url,
        "transports": [
            {"type": "vyandex", "url": doc_url, "priority": 100},
            {"type": "direct", "dial": f"{host}:{port}", "priority": 50}
        ]
    }
    raw = json.dumps(cfg, separators=(',', ':')).encode('utf-8')
    comp_obj = zlib.compressobj(level=9, method=zlib.DEFLATED, wbits=-15)
    compressed = comp_obj.compress(raw) + comp_obj.flush()
    b64 = base64.urlsafe_b64encode(compressed).decode('ascii').rstrip('=')
    return f"openflux://v1/{b64}"


def run_ssh_action(action, params):
    try:
        import paramiko
    except ImportError:
        return {"ok": False, "error": "Библиотека paramiko не найдена. Установите: pip install paramiko"}

    host = params.get("host", "").strip()
    port = int(params.get("port", 22))
    user = params.get("user", "root").strip()
    password = params.get("password", "")
    sudo_pass = params.get("sudoPassword", "")
    doc_url = normalize_yandex_url(params.get("documentUrl", ""))
    channel_port = int(params.get("channelPort", 8445))
    key = params.get("key", "").strip() or secrets.token_hex(32)
    channel = params.get("channel", "").strip() or f"of-{secrets.token_hex(3)}"
    name = params.get("name", "MyOpenFluxNode")

    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())

    try:
        client.connect(hostname=host, port=port, username=user, password=password, timeout=15)
    except Exception as e:
        return {"ok": False, "error": f"Ошибка SSH подключения: {str(e)}"}

    try:
        if action == "probe":
            cmd = f"""
                f=$(mktemp /tmp/openflux-node-install.XXXXXX) ;
                curl -fsSL --retry 3 --connect-timeout 20 -o "$f" '{PINNED_SCRIPT_URL}' || wget -q -T 20 -t 3 -O "$f" '{PINNED_SCRIPT_URL}' ;
                chmod 0700 "$f" ;
                "$f" probe ;
                rm -f "$f"
            """
            stdin, stdout, stderr = client.exec_command(cmd, timeout=30)
            out = stdout.read().decode('utf-8', errors='replace').strip()
            # Поиск последнего JSON объекта
            start = out.rfind('{')
            end = out.rfind('}')
            if start != -1 and end != -1:
                return json.loads(out[start:end+1])
            return {"ok": False, "error": out or stderr.read().decode('utf-8', errors='replace')}

        elif action == "deploy":
            if not doc_url:
                return {"ok": False, "error": "Укажите адрес документа Яндекс"}

            sudo_prefix = "" if user == "root" else (f"echo '{sudo_pass}' | sudo -S " if sudo_pass else "sudo ")
            cfg_content = f"channel={channel}\\nurl={doc_url}\\nkey={key}\\nport={channel_port}\\n"
            cmd = f"""
                f=$(mktemp /tmp/openflux-node-install.XXXXXX) ;
                cfg=$(mktemp /tmp/openflux-cfg.XXXXXX) ;
                chmod 0600 "$cfg" ;
                printf '{cfg_content}' > "$cfg" ;
                curl -fsSL --retry 3 --connect-timeout 20 -o "$f" '{PINNED_SCRIPT_URL}' || wget -q -T 20 -t 3 -O "$f" '{PINNED_SCRIPT_URL}' ;
                chmod 0700 "$f" ;
                {sudo_prefix} "$f" apply "$cfg" ;
                rm -f "$f" "$cfg"
            """
            stdin, stdout, stderr = client.exec_command(cmd, timeout=60)
            out = stdout.read().decode('utf-8', errors='replace').strip()
            start = out.rfind('{')
            end = out.rfind('}')
            res = {}
            if start != -1 and end != -1:
                try:
                    res = json.loads(out[start:end+1])
                except Exception:
                    pass

            if res.get("ok"):
                link = generate_share_link(name, doc_url, key, host, channel_port)
                return {
                    "ok": True,
                    "channel": channel,
                    "key": key,
                    "port": channel_port,
                    "link": link
                }
            return {"ok": False, "error": res.get("error") or out or stderr.read().decode('utf-8', errors='replace')}

    finally:
        client.close()


HTML_PAGE = """<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
<title>OpenFlux Mobile Deployer</title>
<style>
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
    body { background-color: #121216; color: #e4e4eb; padding: 12px; }
    .card { background-color: #1c1c24; border: 1px solid #2e2e3d; border-radius: 8px; padding: 14px; margin-bottom: 14px; }
    h1 { font-size: 18px; font-weight: bold; color: #4da6ff; margin-bottom: 12px; }
    h2 { font-size: 14px; font-weight: bold; color: #70b4ff; margin-bottom: 8px; }
    label { font-size: 12px; color: #a0a0b0; display: block; margin-top: 8px; margin-bottom: 3px; }
    input { width: 100%; background-color: #0f0f14; border: 1px solid #333344; color: #fff; padding: 10px; border-radius: 6px; font-size: 14px; }
    input:focus { border-color: #4da6ff; outline: none; }
    .row { display: flex; gap: 8px; }
    .col { flex: 1; }
    button { width: 100%; padding: 12px; border-radius: 6px; border: none; font-size: 14px; font-weight: bold; cursor: pointer; margin-top: 10px; }
    .btn-primary { background-color: #0066cc; color: #fff; }
    .btn-success { background-color: #1fa353; color: #fff; }
    .btn-secondary { background-color: #2e2e3e; color: #ddd; }
    button:disabled { opacity: 0.5; }
    #log { background-color: #0a0a0e; border: 1px solid #262633; border-radius: 6px; padding: 10px; font-family: monospace; font-size: 12px; min-height: 90px; max-height: 180px; overflow-y: auto; color: #a8b8cc; margin-top: 10px; }
    .hint { font-size: 11px; color: #6a6a7c; margin-top: 3px; }
    .res-box { background-color: #14281e; border: 1px solid #287844; padding: 12px; border-radius: 6px; margin-top: 10px; display: none; }
    .link-text { word-break: break-all; font-family: monospace; font-size: 12px; color: #55ee88; background: #0c1a12; padding: 8px; border-radius: 4px; margin: 8px 0; }
</style>
</head>
<body>

<h1>OpenFlux Node Deployer (Android)</h1>

<div class="card">
    <h2>1. Сервер VDS / VPS (SSH)</h2>
    <label>IP-адрес сервера:</label>
    <input type="text" id="host" placeholder="194.87.100.25">
    
    <div class="row">
        <div class="col">
            <label>SSH Порт:</label>
            <input type="number" id="port" value="22">
        </div>
        <div class="col">
            <label>Пользователь:</label>
            <input type="text" id="user" value="root">
        </div>
    </div>

    <label>Пароль SSH:</label>
    <input type="password" id="password" placeholder="Пароль root">

    <label>Пароль Sudo (если не root):</label>
    <input type="password" id="sudoPassword" placeholder="Оставьте пустым для root">
</div>

<div class="card">
    <h2>2. Параметры канала OpenFlux</h2>
    <div class="row">
        <div class="col">
            <label>Имя ноды:</label>
            <input type="text" id="name" value="MyOpenFluxNode">
        </div>
        <div class="col">
            <label>Direct порт:</label>
            <input type="number" id="channelPort" value="8445">
        </div>
    </div>

    <label>URL документа Яндекс:</label>
    <input type="text" id="documentUrl" placeholder="https://docs.yandex.ru/edit/d/...">
    <div class="hint">Параметры ?from_public=1 очищаются автоматически</div>

    <label>Ключ шифрования AES-256 (64 hex):</label>
    <div class="row">
        <div class="col" style="flex: 3;">
            <input type="text" id="key" placeholder="Автогенерация при деплое">
        </div>
        <div class="col" style="flex: 1;">
            <button class="btn-secondary" style="margin-top:0;" onclick="genKey()">Ключ</button>
        </div>
    </div>
</div>

<div class="card">
    <h2>3. Действия</h2>
    <button class="btn-secondary" id="btnProbe" onclick="doAction('probe')">Проверить сервер</button>
    <button class="btn-success" id="btnDeploy" onclick="doAction('deploy')">Развернуть ноду (Apply)</button>

    <div class="res-box" id="resBox">
        <div style="font-weight: bold; color: #55ee88;">Нода успешно развернута!</div>
        <div class="link-text" id="shareLinkText"></div>
        <button class="btn-primary" onclick="copyLink()">Копировать ссылку openflux://</button>
    </div>

    <label>Журнал выполнения:</label>
    <div id="log">Ожидание команд...</div>
</div>

<script>
    function genKey() {
        const arr = new Uint8Array(32);
        window.crypto.getRandomValues(arr);
        const hex = Array.from(arr).map(b => b.toString(16).padStart(2, '0')).join('');
        document.getElementById('key').value = hex;
        log('[КЛЮЧ] Сгенерирован 256-битный ключ');
    }

    function log(msg) {
        const el = document.getElementById('log');
        el.innerText += '\\n' + msg;
        el.scrollTop = el.scrollHeight;
    }

    function cleanUrl(url) {
        let u = url.trim();
        if (u.includes('#')) u = u.split('#')[0];
        if (u.includes('?')) u = u.split('?')[0];
        u = u.replace(/\\/+$/, '');
        const m = u.match(/https?:\\/\\/(?:docs|disk)\\.yandex\\.(?:ru|com|by|kz|uz)\\/edit\\/d\\/([A-Za-z0-9_-]{16,200})/);
        if (m) return 'https://docs.yandex.ru/edit/d/' + m[1];
        return u;
    }

    async function doAction(action) {
        const host = document.getElementById('host').value.trim();
        if (!host) { alert('Укажите IP адрес сервера'); return; }

        let docUrl = cleanUrl(document.getElementById('documentUrl').value);
        document.getElementById('documentUrl').value = docUrl;

        const body = {
            action: action,
            host: host,
            port: document.getElementById('port').value,
            user: document.getElementById('user').value,
            password: document.getElementById('password').value,
            sudoPassword: document.getElementById('sudoPassword').value,
            name: document.getElementById('name').value,
            channelPort: document.getElementById('channelPort').value,
            documentUrl: docUrl,
            key: document.getElementById('key').value
        };

        document.getElementById('btnProbe').disabled = true;
        document.getElementById('btnDeploy').disabled = true;
        log('[СТАРТ] Выполнение ' + action + '...');

        try {
            const resp = await fetch('/api', {
                method: 'POST',
                headers: {'Content-Type': 'application/json'},
                body: JSON.stringify(body)
            });
            const res = await resp.json();

            if (res.ok) {
                if (action === 'probe') {
                    log('[УСПЕХ] ОС: ' + res.os + ', Arch: ' + res.arch + ', Systemd: ' + res.systemd + ', Sudo: ' + res.sudo);
                } else if (action === 'deploy') {
                    log('[ГОТОВО] Нода установлена! Порт: ' + res.port);
                    document.getElementById('resBox').style.display = 'block';
                    document.getElementById('shareLinkText').innerText = res.link;
                }
            } else {
                log('[ОШИБКА] ' + (res.error || 'Неизвестная ошибка'));
                alert('Ошибка: ' + (res.error || 'Сбой операции'));
            }
        } catch (e) {
            log('[ИСКЛЮЧЕНИЕ] ' + e.message);
        } finally {
            document.getElementById('btnProbe').disabled = false;
            document.getElementById('btnDeploy').disabled = false;
        }
    }

    function copyLink() {
        const text = document.getElementById('shareLinkText').innerText;
        navigator.clipboard.writeText(text).then(() => {
            alert('Ссылка openflux:// скопирована в буфер обмена');
        });
    }
</script>
</body>
</html>
"""


class MobileDeployerHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.end_headers()
        self.wfile.write(HTML_PAGE.encode("utf-8"))

    def do_POST(self):
        if self.path == "/api":
            length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(length).decode("utf-8")
            data = json.loads(body)
            action = data.get("action", "probe")
            result = run_ssh_action(action, data)

            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.end_headers()
            self.wfile.write(json.dumps(result, ensure_ascii=False).encode("utf-8"))
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        # Отключаем спам в терминале
        pass


def main():
    port = 8080
    if len(sys.argv) > 1:
        try:
            port = int(sys.argv[1].replace("--port=", ""))
        except Exception:
            pass

    server = HTTPServer(("0.0.0.0", port), MobileDeployerHandler)
    print(f"[ИНФО] OpenFlux Mobile Deployer запущен на порту {port}")
    print(f"[ИНФО] На телефоне откройте: http://localhost:{port} (в Termux) или http://<IP_ПК>:{port}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[ОСТАНОВЛЕНО] Сервер выключен")


if __name__ == "__main__":
    main()
