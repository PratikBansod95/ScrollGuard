package com.scrollguard.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.scrollguard.R

sealed class OverlayCommand {
    data class ShowTimer(
        val appName: String,
        val totalSeconds: Int,
        val remainingSeconds: Int,
    ) : OverlayCommand()

    data class ShowWarning(
        val title: String,
        val message: String,
    ) : OverlayCommand()

    data class ShowBlock(
        val appName: String,
        val cooldownSeconds: Int,
    ) : OverlayCommand()

    data object HideAll : OverlayCommand()
}

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var timerView: View? = null
    private var blockView: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getStringExtra(EXTRA_COMMAND)) {
            COMMAND_TIMER -> showTimer(
                appName = intent.getStringExtra(EXTRA_APP_NAME).orEmpty(),
                totalSeconds = intent.getIntExtra(EXTRA_TOTAL_SECONDS, 1),
                remainingSeconds = intent.getIntExtra(EXTRA_REMAINING_SECONDS, 0),
            )
            COMMAND_WARNING -> showWarning(
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty(),
            )
            COMMAND_BLOCK -> showBlock(
                appName = intent.getStringExtra(EXTRA_APP_NAME).orEmpty(),
                cooldownSeconds = intent.getIntExtra(EXTRA_COOLDOWN_SECONDS, 0),
            )
            COMMAND_HIDE -> hideAll()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        hideAll()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showTimer(appName: String, totalSeconds: Int, remainingSeconds: Int) {
        removeView(timerView)
        removeView(blockView)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xDD203A43.toInt())
            setPadding(32, 24, 32, 24)
        }
        val title = TextView(this).apply {
            text = "$appName  ${formatSeconds(remainingSeconds)} left"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
        }
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = totalSeconds.coerceAtLeast(1)
            progress = remainingSeconds.coerceAtLeast(0)
        }
        container.addView(title)
        container.addView(progress)
        timerView = container
        addView(container, topParams())
    }

    private fun showWarning(title: String, message: String) {
        removeView(blockView)
        val root = FrameLayout(this).apply {
            setBackgroundColor(0x66B54708.toInt())
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(48, 48, 48, 48)
        }
        content.addView(TextView(this).apply {
            text = title
            textSize = 20f
        })
        content.addView(TextView(this).apply {
            text = message
            textSize = 16f
        })
        root.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        blockView = root
        addView(root, centeredParams())
    }

    private fun showBlock(appName: String, cooldownSeconds: Int) {
        removeView(blockView)
        val root = FrameLayout(this).apply {
            setBackgroundColor(0xEE101828.toInt())
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        content.addView(TextView(this).apply {
            text = "Time's up. Take a break."
            textSize = 26f
            setTextColor(0xFFFFFFFF.toInt())
        })
        content.addView(TextView(this).apply {
            text = "$appName is blocked for ${formatSeconds(cooldownSeconds)}"
            textSize = 18f
            setTextColor(0xFFE5E7EB.toInt())
        })
        root.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        blockView = root
        addView(root, fullScreenParams())
    }

    private fun addView(view: View, params: WindowManager.LayoutParams) {
        if (view.parent == null) {
            windowManager.addView(view, params)
        }
    }

    private fun hideAll() {
        removeView(timerView)
        removeView(blockView)
        timerView = null
        blockView = null
    }

    private fun removeView(view: View?) {
        if (view != null && view.parent != null) {
            windowManager.removeView(view)
        }
    }

    private fun topParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
        }
    }

    private fun centeredParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
        }
    }

    private fun fullScreenParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "ScrollGuard overlays",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("ScrollGuard overlay active")
            .setContentText("Showing timer and block overlays when limits trigger.")
            .setOngoing(true)
            .build()
    }

    private fun formatSeconds(totalSeconds: Int): String {
        val safeSeconds = totalSeconds.coerceAtLeast(0)
        val minutes = safeSeconds / 60
        val seconds = safeSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }

    companion object {
        private const val CHANNEL_ID = "overlay_channel"
        private const val NOTIFICATION_ID = 2002
        private const val EXTRA_COMMAND = "command"
        private const val EXTRA_APP_NAME = "app_name"
        private const val EXTRA_TOTAL_SECONDS = "total_seconds"
        private const val EXTRA_REMAINING_SECONDS = "remaining_seconds"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_COOLDOWN_SECONDS = "cooldown_seconds"
        private const val COMMAND_TIMER = "timer"
        private const val COMMAND_WARNING = "warning"
        private const val COMMAND_BLOCK = "block"
        private const val COMMAND_HIDE = "hide"

        fun createIntent(context: Context, command: OverlayCommand): Intent {
            return Intent(context, OverlayService::class.java).apply {
                when (command) {
                    is OverlayCommand.ShowTimer -> {
                        putExtra(EXTRA_COMMAND, COMMAND_TIMER)
                        putExtra(EXTRA_APP_NAME, command.appName)
                        putExtra(EXTRA_TOTAL_SECONDS, command.totalSeconds)
                        putExtra(EXTRA_REMAINING_SECONDS, command.remainingSeconds)
                    }
                    is OverlayCommand.ShowWarning -> {
                        putExtra(EXTRA_COMMAND, COMMAND_WARNING)
                        putExtra(EXTRA_TITLE, command.title)
                        putExtra(EXTRA_MESSAGE, command.message)
                    }
                    is OverlayCommand.ShowBlock -> {
                        putExtra(EXTRA_COMMAND, COMMAND_BLOCK)
                        putExtra(EXTRA_APP_NAME, command.appName)
                        putExtra(EXTRA_COOLDOWN_SECONDS, command.cooldownSeconds)
                    }
                    OverlayCommand.HideAll -> putExtra(EXTRA_COMMAND, COMMAND_HIDE)
                }
            }
        }
    }
}
