package com.scrollguard.services

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.scrollguard.R
import com.scrollguard.ScrollGuardApp

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
    private val mainHandler = Handler(Looper.getMainLooper())

    private var timerView: FrameLayout? = null
    private var timerAppView: TextView? = null
    private var timerRemainingView: TextView? = null
    private var timerProgressView: ProgressBar? = null

    private var warningView: FrameLayout? = null
    private var warningCardView: LinearLayout? = null
    private var warningTitleView: TextView? = null
    private var warningMessageView: TextView? = null

    private var blockView: FrameLayout? = null
    private var blockCardView: LinearLayout? = null
    private var blockBadgeView: TextView? = null
    private var blockTitleView: TextView? = null
    private var blockMessageView: TextView? = null
    private var activeBlockedAppName: String? = null
    private var cooldownEndTimeMillis = 0L

    private val cooldownTicker = object : Runnable {
        override fun run() {
            if (blockView?.visibility != View.VISIBLE || cooldownEndTimeMillis <= 0L) {
                return
            }
            val remainingSeconds = ((cooldownEndTimeMillis - System.currentTimeMillis()) / 1000L)
                .toInt()
                .coerceAtLeast(0)
            updateBlockCountdown(remainingSeconds)
            if (remainingSeconds > 0) {
                mainHandler.postDelayed(this, 1_000L)
            }
        }
    }

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
        ensureTimerView()
        stopCooldownTicker()
        timerAppView?.text = appName
        timerRemainingView?.text = "${formatSeconds(remainingSeconds)} left"
        timerProgressView?.max = totalSeconds.coerceAtLeast(1)
        timerProgressView?.progress = remainingSeconds.coerceAtLeast(0)

        animateOut(warningView)
        if (blockView?.visibility == View.VISIBLE) {
            animateOut(timerView)
        } else {
            animateIn(
                view = timerView,
                fromTranslationY = -dp(18).toFloat(),
                toTranslationY = 0f,
                fromScale = 1f,
                toScale = 1f,
                duration = 220L,
            )
        }
    }

    private fun showWarning(title: String, message: String) {
        ensureWarningView()
        warningTitleView?.text = title
        warningMessageView?.text = message
        animateIn(
            view = warningView,
            fromTranslationY = dp(12).toFloat(),
            toTranslationY = 0f,
            fromScale = 1f,
            toScale = 1f,
            duration = 180L,
        )
        animateIn(
            view = warningCardView,
            fromTranslationY = dp(18).toFloat(),
            toTranslationY = 0f,
            fromScale = 0.96f,
            toScale = 1f,
            duration = 240L,
            useOvershoot = true,
        )
    }

    private fun showBlock(appName: String, cooldownSeconds: Int) {
        ensureBlockView()
        activeBlockedAppName = appName
        cooldownEndTimeMillis = System.currentTimeMillis() + cooldownSeconds.coerceAtLeast(0) * 1_000L
        updateBlockCountdown(cooldownSeconds)
        animateOut(timerView)
        animateOut(warningView)
        animateIn(
            view = blockView,
            fromTranslationY = 0f,
            toTranslationY = 0f,
            fromScale = 1f,
            toScale = 1f,
            duration = 220L,
        )
        animateIn(
            view = blockCardView,
            fromTranslationY = dp(14).toFloat(),
            toTranslationY = 0f,
            fromScale = 0.98f,
            toScale = 1f,
            duration = 260L,
        )
        startCooldownTicker()
    }

    private fun updateBlockCountdown(cooldownSeconds: Int) {
        blockBadgeView?.text = "Cooldown active"
        blockTitleView?.text = "Put the phone down for a minute"
        val appName = activeBlockedAppName.orEmpty()
        blockMessageView?.text = "$appName is locked for ${formatSeconds(cooldownSeconds)} so this session can reset."
    }

    private fun startCooldownTicker() {
        mainHandler.removeCallbacks(cooldownTicker)
        mainHandler.post(cooldownTicker)
    }

    private fun stopCooldownTicker() {
        mainHandler.removeCallbacks(cooldownTicker)
    }

    private fun ensureTimerView() {
        if (timerView != null) return

        val root = FrameLayout(this).apply {
            visibility = View.GONE
            alpha = 0f
            setPadding(dp(12), dp(12), dp(12), 0)
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(
                color = Color.parseColor("#E217313A"),
                radiusDp = 22,
                strokeColor = Color.parseColor("#33FFFFFF"),
                strokeDp = 1,
            )
            elevation = dp(8).toFloat()
            setPadding(dp(18), dp(14), dp(18), dp(16))
        }
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val appLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val remainingLabel = TextView(this).apply {
            setTextColor(Color.parseColor("#FFE7B76A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedDrawable(Color.parseColor("#1FFFFFFF"), 999)
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            progressDrawable.setTint(Color.parseColor("#FFE7B76A"))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#33FFFFFF"))
        }

        topRow.addView(appLabel)
        topRow.addView(remainingLabel)
        card.addView(topRow)
        card.addView(space(dp(10)))
        card.addView(progress, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(8),
        ))
        root.addView(card)

        timerView = root
        timerAppView = appLabel
        timerRemainingView = remainingLabel
        timerProgressView = progress
        addView(root, topParams())
    }

    private fun ensureWarningView() {
        if (warningView != null) return

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#80311D09"))
            visibility = View.GONE
            alpha = 0f
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(Color.parseColor("#FFF9F4EA"), 28)
            setPadding(dp(24), dp(24), dp(24), dp(20))
            elevation = dp(10).toFloat()
        }
        val eyebrow = TextView(this).apply {
            text = "SCROLL CHECK"
            setTextColor(Color.parseColor("#B54708"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        val title = TextView(this).apply {
            setTextColor(Color.parseColor("#172B35"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTypeface(typeface, Typeface.BOLD)
        }
        val message = TextView(this).apply {
            setTextColor(Color.parseColor("#5E6A70"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setLineSpacing(0f, 1.15f)
        }
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val continueButton = actionButton(
            text = "Continue 10s",
            backgroundColor = Color.parseColor("#17313A"),
            textColor = Color.WHITE,
        ) {
            val sessionManager = (application as ScrollGuardApp).container.sessionManager
            sessionManager.extendCurrentSession(10)
            animateOut(warningView)
        }
        val closeButton = actionButton(
            text = "Close app",
            backgroundColor = Color.parseColor("#E7DED1"),
            textColor = Color.parseColor("#172B35"),
        ) {
            startActivity(
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            animateOut(warningView)
        }

        buttonRow.addView(closeButton)
        buttonRow.addView(space(dp(10), horizontal = true))
        buttonRow.addView(continueButton)

        card.addView(eyebrow)
        card.addView(space(dp(10)))
        card.addView(title)
        card.addView(space(dp(12)))
        card.addView(message)
        card.addView(space(dp(22)))
        card.addView(buttonRow)
        root.addView(card, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        ))

        warningView = root
        warningCardView = card
        warningTitleView = title
        warningMessageView = message
        addView(root, interactiveParams())
    }

    private fun ensureBlockView() {
        if (blockView != null) return

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#E6111B22"))
            visibility = View.GONE
            alpha = 0f
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = roundedDrawable(Color.parseColor("#FF132731"), 32)
            setPadding(dp(28), dp(28), dp(28), dp(28))
            elevation = dp(12).toFloat()
        }
        val badge = TextView(this).apply {
            setTextColor(Color.parseColor("#FFE7B76A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            background = roundedDrawable(Color.parseColor("#1FFFFFFF"), 999)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val title = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val message = TextView(this).apply {
            setTextColor(Color.parseColor("#D7E0E4"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.15f)
        }
        val hint = TextView(this).apply {
            text = "The block will lift automatically when the cooldown ends."
            setTextColor(Color.parseColor("#8FA5AE"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER
        }

        card.addView(badge)
        card.addView(space(dp(20)))
        card.addView(title)
        card.addView(space(dp(12)))
        card.addView(message)
        card.addView(space(dp(18)))
        card.addView(hint)
        root.addView(card, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        ))

        blockView = root
        blockCardView = card
        blockBadgeView = badge
        blockTitleView = title
        blockMessageView = message
        addView(root, fullScreenParams())
    }

    private fun actionButton(
        text: String,
        backgroundColor: Int,
        textColor: Int,
        onClick: () -> Unit,
    ): Button {
        return Button(this).apply {
            this.text = text
            setTextColor(textColor)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            background = roundedDrawable(backgroundColor, 999)
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener { onClick() }
        }
    }

    private fun animateIn(
        view: View?,
        fromTranslationY: Float,
        toTranslationY: Float,
        fromScale: Float,
        toScale: Float,
        duration: Long,
        useOvershoot: Boolean = false,
    ) {
        view ?: return
        if (view.visibility == View.VISIBLE && view.alpha == 1f && view.translationY == toTranslationY) {
            return
        }
        view.animate().cancel()
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.translationY = fromTranslationY
        view.scaleX = fromScale
        view.scaleY = fromScale
        view.animate()
            .alpha(1f)
            .translationY(toTranslationY)
            .scaleX(toScale)
            .scaleY(toScale)
            .setDuration(duration)
            .setInterpolator(if (useOvershoot) OvershootInterpolator(0.8f) else android.view.animation.DecelerateInterpolator())
            .start()
    }

    private fun animateOut(view: View?) {
        view ?: return
        if (view.visibility != View.VISIBLE) {
            view.alpha = 0f
            return
        }
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .translationY(view.translationY - dp(6))
            .setDuration(140L)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.visibility = View.GONE
                    view.translationY = 0f
                    view.scaleX = 1f
                    view.scaleY = 1f
                    view.animate().setListener(null)
                }
            })
            .start()
    }

    private fun addView(view: View, params: WindowManager.LayoutParams) {
        if (view.parent == null) {
            windowManager.addView(view, params)
        }
    }

    private fun hideAll() {
        stopCooldownTicker()
        removeView(timerView)
        removeView(warningView)
        removeView(blockView)
        timerView = null
        timerAppView = null
        timerRemainingView = null
        timerProgressView = null
        warningView = null
        warningCardView = null
        warningTitleView = null
        warningMessageView = null
        blockView = null
        blockCardView = null
        blockBadgeView = null
        blockTitleView = null
        blockMessageView = null
        activeBlockedAppName = null
        cooldownEndTimeMillis = 0L
    }

    private fun removeView(view: View?) {
        if (view != null && view.parent != null) {
            windowManager.removeViewImmediate(view)
        }
    }

    private fun topParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
        }
    }

    private fun interactiveParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
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

    private fun roundedDrawable(
        color: Int,
        radiusDp: Int,
        strokeColor: Int? = null,
        strokeDp: Int = 0,
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(color)
            if (strokeColor != null && strokeDp > 0) {
                setStroke(dp(strokeDp), strokeColor)
            }
        }
    }

    private fun space(size: Int, horizontal: Boolean = false): View {
        return View(this).apply {
            layoutParams = if (horizontal) {
                LinearLayout.LayoutParams(size, 1)
            } else {
                LinearLayout.LayoutParams(1, size)
            }
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics,
        ).toInt()
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
