package com.example.template

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.template.ui.theme.TemplateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge is what makes the drawer's sliding "card" read correctly; the sidebar
        // then applies the status-bar inset itself (see docs/PITFALLS.md).
        enableEdgeToEdge()
        setContent {
            TemplateTheme {
                TemplateApp()
            }
        }
    }
}
