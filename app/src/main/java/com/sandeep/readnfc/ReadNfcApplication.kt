package com.sandeep.readnfc

import android.app.Application

class ReadNfcApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            FlipperInitializr.getInstance(applicationContext)
        }
    }
}
