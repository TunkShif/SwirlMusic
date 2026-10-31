package one.tunkshif.swirlmusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import one.tunkshif.swirlmusic.ui.shell.SwirlApp
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SwirlMusicTheme {
                SwirlApp()
            }
        }
    }
}
