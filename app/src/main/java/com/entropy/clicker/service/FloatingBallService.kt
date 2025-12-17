package com.entropy.clicker.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.entropy.clicker.R
import com.entropy.clicker.data.model.BallState
import com.entropy.clicker.data.model.ClickConfig
import com.entropy.clicker.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 悬浮球服务
 */
class FloatingBallService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "entropy_floating_ball"

        const val ACTION_UPDATE_CONFIG = "com.entropy.clicker.UPDATE_CONFIG"
        const val EXTRA_CONFIG = "config"

        private var currentConfig: ClickConfig = ClickConfig.DEFAULT

        fun setConfig(config: ClickConfig) {
            currentConfig = config
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var ballText: TextView

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var ballState: BallState = BallState.IDLE
    private var clickCount: Int = 0

    // 触摸状态
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastClickTime = 0L
    private var isMoving = false

    // 状态更新广播接收器
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ClickAccessibilityService.ACTION_STATE_CHANGED) {
                val stateName = intent.getStringExtra(ClickAccessibilityService.EXTRA_STATE)
                val count = intent.getIntExtra(ClickAccessibilityService.EXTRA_CLICK_COUNT, 0)
                stateName?.let {
                    updateBallState(BallState.valueOf(it), count)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingBall()
        createNotificationChannel()

        // 注册广播接收器
        val filter = IntentFilter(ClickAccessibilityService.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stateReceiver, filter)
        }

        // 设置无障碍服务的状态回调
        ClickAccessibilityService.instance?.onStateChanged = { state, count ->
            serviceScope.launch {
                updateBallState(state, count)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())

        // 处理配置更新
        intent?.let {
            if (it.action == ACTION_UPDATE_CONFIG) {
                // 配置已通过 companion object 更新
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()

        try {
            unregisterReceiver(stateReceiver)
        } catch (e: Exception) {
            // 忽略
        }

        try {
            windowManager.removeView(floatingView)
        } catch (e: Exception) {
            // 忽略
        }

        ClickAccessibilityService.instance?.onStateChanged = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun createFloatingBall() {
        // 创建悬浮窗布局参数
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            dp(56),
            dp(56),
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - dp(70)
            y = resources.displayMetrics.heightPixels / 2
        }

        // 创建悬浮球视图
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_ball, null)
        ballText = floatingView.findViewById(R.id.ball_text)

        // 设置触摸监听
        floatingView.setOnTouchListener { _, event ->
            handleTouch(event)
            true
        }

        // 添加到窗口
        windowManager.addView(floatingView, layoutParams)
        updateBallUI()
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = layoutParams.x
                initialY = layoutParams.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isMoving = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY
                if (dx * dx + dy * dy > 100) { // 移动阈值
                    isMoving = true
                    layoutParams.x = initialX + dx.toInt()
                    layoutParams.y = initialY + dy.toInt()
                    windowManager.updateViewLayout(floatingView, layoutParams)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isMoving) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastClickTime < 300) {
                        // 双击：停止并重置
                        onDoubleClick()
                    } else {
                        // 单击：启动/暂停
                        onSingleClick()
                    }
                    lastClickTime = currentTime
                }
            }
        }
        return true
    }

    private fun onSingleClick() {
        val service = ClickAccessibilityService.instance
        if (service == null) {
            // 无障碍服务未启动，打开主界面
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return
        }

        // 切换状态
        service.toggle(currentConfig)
    }

    private fun onDoubleClick() {
        ClickAccessibilityService.instance?.stopClicking()
    }

    private fun updateBallState(state: BallState, count: Int) {
        ballState = state
        clickCount = count
        updateBallUI()
    }

    private fun updateBallUI() {
        // 更新背景颜色
        val backgroundRes = when (ballState) {
            BallState.IDLE -> R.drawable.ball_idle
            BallState.RUNNING -> R.drawable.ball_running
            BallState.PAUSED -> R.drawable.ball_paused
        }
        floatingView.setBackgroundResource(backgroundRes)

        // 更新文字
        ballText.text = when (ballState) {
            BallState.IDLE -> "E"
            BallState.RUNNING, BallState.PAUSED -> {
                if (clickCount > 999) "999+" else clickCount.toString()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮球服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持悬浮球运行"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.floating_ball_notification_title))
            .setContentText(getString(R.string.floating_ball_notification_content))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
