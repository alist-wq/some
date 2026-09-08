package com.example.navigation.app

import android.app.Application

class RenavApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RenavDiagnostics.initialize(this)
        RenavDiagnostics.event("application_created")
    }
}
