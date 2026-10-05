package io.github.phiscript

import androidx.lifecycle.ViewModel
import java.util.concurrent.Executors

/** Keep long APK copies and their busy state across Activity configuration changes. */
class LibraryWork : ViewModel() {
    val worker = Executors.newSingleThreadExecutor()
    @Volatile var scanning = false

    override fun onCleared() {
        worker.shutdownNow()
        super.onCleared()
    }
}
