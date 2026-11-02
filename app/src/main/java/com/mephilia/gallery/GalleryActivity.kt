package com.mephilia.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mephilia.gallery.ui.GalleryRoot
import com.mephilia.gallery.ui.theme.GalleryTheme

/** The app's main window: the gallery, drawn edge to edge at the screen's highest refresh rate. */
class GalleryActivity : ComponentActivity() {
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
