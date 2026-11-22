package app.sigmatube.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.sigmatube.MainActivity
import app.sigmatube.SigmaTubeApp

/**
 * Keeps playback alive outside the app (screen off, app closed) and provides the media
 * notification + lock-screen controls. It wraps the single app-wide player.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as SigmaTubeApp).container
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, container.playerManager.sessionPlayer)
            .setSessionActivity(openApp)
            .build()
    }

    /** Only background-able videos (music by default) get a notification / lock-screen controls. */
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val container = (application as SigmaTubeApp).container
        if (container.playerManager.backgroundAllowed) {
            super.onUpdateNotification(session, startInForegroundRequired)
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            NotificationManagerCompat.from(this).cancel(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** App swiped away from recents: keep going only if something is actually playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
