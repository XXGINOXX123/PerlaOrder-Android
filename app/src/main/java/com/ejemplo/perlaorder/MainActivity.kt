package com.ejemplo.perlaorder

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ejemplo.perlaorder.view.menu.MenuScreen
import com.ejemplo.perlaorder.view.theme.PerlaOrderTheme
import com.ejemplo.perlaorder.viewmodel.MenuViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MenuViewModel by viewModels {
        viewModelFactory {
            initializer { MenuViewModel((application as PerlaOrderApp).menuRepository) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { PerlaOrderTheme { MenuScreen(viewModel) } }
    }
}