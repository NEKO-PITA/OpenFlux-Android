package ru.openflux.deployer

import android.app.Application
import ru.openflux.deployer.core.ConnectionRepository

class OpenFluxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ConnectionRepository.initialize(this)
    }
}
