package com.techmare.pricewatch

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.techmare.pricewatch.ui.App
import com.techmare.pricewatch.ui.PriceTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            val prefs = com.techmare.pricewatch.data.Prefs(this)
            PriceWorker.schedule(this, prefs.notify)
            if (Build.VERSION.SDK_INT >= 33 && prefs.notify) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { PriceTheme { App(vm) } }
    }
}
