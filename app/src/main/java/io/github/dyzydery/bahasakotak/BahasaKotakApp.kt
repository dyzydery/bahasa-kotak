package io.github.dyzydery.bahasakotak

import android.app.Application

class BahasaKotakApp : Application() {
    val container by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        container.start()
    }
}
