package de.dealcheck.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.dealcheck.app.ui.DealCheckNav
import de.dealcheck.app.ui.NewAnalysisViewModel
import de.dealcheck.app.ui.UpdateDialog
import de.dealcheck.app.ui.theme.DealCheckTheme

class MainActivity : ComponentActivity() {

    private val newAnalysisViewModel: NewAnalysisViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application as DealCheckApp
                NewAnalysisViewModel(app.repository, app.settings)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            handleShareIntent(intent)
            (application as DealCheckApp).updates.checkOnStartup()
        }
        val updates = (application as DealCheckApp).updates
        setContent {
            DealCheckTheme {
                DealCheckNav(newAnalysisViewModel)
                UpdateDialog(updates)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    /** Bilder, die per Android-„Teilen“ aus anderen Apps kommen. */
    private fun handleShareIntent(intent: Intent?) {
        val uris: List<Uri> = when (intent?.action) {
            Intent.ACTION_SEND ->
                listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }.ifEmpty {
            // Manche Apps legen die Bilder nur in ClipData ab
            val clip = intent?.clipData
            if (intent?.action in listOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE) && clip != null) {
                (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
            } else {
                emptyList()
            }
        }
        if (uris.isNotEmpty()) newAnalysisViewModel.addImages(uris, fromShare = true)
    }
}
