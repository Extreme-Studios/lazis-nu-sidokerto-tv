package lazisnu.sidokerto

import android.app.Application
import com.google.android.gms.ads.MobileAds
import lazisnu.sidokerto.updates.ContentUpdateScheduler

class LazisNuApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        MobileAds.initialize(this) {}
        
        ContentUpdateScheduler.ensureScheduled(this)
    }
}
