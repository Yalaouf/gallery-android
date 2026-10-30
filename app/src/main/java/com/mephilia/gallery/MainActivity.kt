package com.mephilia.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mephilia.gallery.ui.GalleryRoot
import com.mephilia.gallery.ui.theme.GalleryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestHighestRefreshRate()
        setContent {
            GalleryTheme {
                GalleryRoot()
            }
        }
    }
}
