package com.swadsangam.restaurant

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.swadsangam.restaurant.data.prefs.UserPrefs
import com.swadsangam.restaurant.ui.AppViewModel
import com.swadsangam.restaurant.ui.navigation.AppNav
import com.swadsangam.restaurant.ui.theme.SwadSangamTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val appVm: AppViewModel by viewModels()
    @Inject lateinit var prefs: UserPrefs
    private var deepLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        // Hold the splash until the cached menu is ready — at most ~1.5 s (FR-01)
        splash.setKeepOnScreenCondition { !appVm.ready.value }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLink = intent?.dataString
        setContent {
            SwadSangamTheme {
                AppNav(appVm, prefs, deepLink) { deepLink = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let { deepLink = it }
    }

    override fun onStart() {
        super.onStart()
        appVm.refresh()
    }
}
