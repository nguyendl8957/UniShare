package vn.unishare

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class DebugUniShareApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)
        // Use Firebase Cloud for Android Debug.
        // Change to true only when running local Emulator tests.
        val useFirebaseEmulators = false

        if (useFirebaseEmulators) {
            val host = "127.0.0.1"
            FirebaseAuth.getInstance().useEmulator(host, 9099)
            FirebaseFirestore.getInstance().useEmulator(host, 8080)
            FirebaseStorage.getInstance().useEmulator(host, 9199)
        }
    }
}