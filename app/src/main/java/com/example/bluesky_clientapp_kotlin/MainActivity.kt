package com.example.bluesky_clientapp_kotlin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.bluesky_clientapp_kotlin.ui.BlueskyClientApp
import com.example.bluesky_clientapp_kotlin.ui.theme.Bluesky_clientapp_kotlinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Bluesky_clientapp_kotlinTheme {
                BlueskyClientApp()
            }
        }
    }
}
