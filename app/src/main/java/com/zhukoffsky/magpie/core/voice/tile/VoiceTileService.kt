package com.zhukoffsky.magpie.core.voice.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.zhukoffsky.magpie.core.voice.VoiceCaptureActivity
import com.zhukoffsky.magpie.core.voice.VoiceTarget

/**
 * Плитка в шторке: тап — и сразу распознавание, без открытия приложения.
 *
 * Это самый быстрый путь к записи, ради него всё и затевалось.
 */
abstract class VoiceTileService : TileService() {

    protected abstract val target: VoiceTarget

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()

        val pendingIntent = PendingIntent.getActivity(
            this,
            target.ordinal,
            VoiceCaptureActivity.intent(this, target)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // На заблокированном экране система сначала попросит разблокировать:
        // обойти это нельзя, распознавание речи поверх лока не запускается.
        if (isLocked) {
            unlockAndRun { launch(pendingIntent) }
        } else {
            launch(pendingIntent)
        }
    }

    private fun launch(pendingIntent: PendingIntent) {
        // С Android 14 вариант с Intent выбрасывает исключение: система
        // требует PendingIntent, чтобы запуск нельзя было подделать.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(pendingIntent)
        } else {
            // Устаревший вариант нужен именно здесь: до Android 14 другого
            // нет. `@Suppress` глушит компилятор, `@SuppressLint` — проверку
            // lint, у неё отдельный идентификатор.
            @Suppress("DEPRECATION")
            @SuppressLint("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(VoiceCaptureActivity.intent(this, target))
        }
    }
}

class ShoppingVoiceTileService : VoiceTileService() {
    override val target = VoiceTarget.SHOPPING
}

class ReminderVoiceTileService : VoiceTileService() {
    override val target = VoiceTarget.REMINDER
}
