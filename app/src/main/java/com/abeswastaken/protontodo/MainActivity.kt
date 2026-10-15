package com.abeswastaken.protontodo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.abeswastaken.protontodo.ui.ProtonTodoApp
import com.abeswastaken.protontodo.ui.theme.ProtonTodoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ProtonTodoTheme {
                ProtonTodoApp()
            }
        }
    }
}
