package io.github.secnewt.reader.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import io.github.secnewt.reader.MainActivity

/** Quick Settings "Read screen" tile. If Reader is off, it opens the setup screen instead. */
class ReaderTileService : TileService() {
    override fun onClick() {
        val service = ReaderService.running
        if (service != null) {
            service.openFromTile()
            return
        }
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
