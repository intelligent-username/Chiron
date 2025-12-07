package com.chiron.app

import android.app.Application
import com.chiron.app.di.ServiceLocator

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
