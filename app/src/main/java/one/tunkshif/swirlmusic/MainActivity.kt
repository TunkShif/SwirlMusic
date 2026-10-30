package one.tunkshif.swirlmusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import one.tunkshif.swirlmusic.ui.placeholder.PlaceholderRoute
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme

/**
 * Phase 0 has one screen: the foundation check. The real shell replaces it in Phase 1.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SwirlMusicTheme {
                PlaceholderRoute()
            }
        }
    }
}
