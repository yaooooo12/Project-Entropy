package com.entropy.clicker.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.entropy.clicker.R

class OverlaySetupService : Service() {

    companion object {
        const val ACTION_SCOPE_RESULT = "com.entropy.clicker.SCOPE_RESULT"
        const val EXTRA_X_RATIO = "x_ratio"
        const val EXTRA_Y_RATIO = "y_ratio"
        const val EXTRA_JITTER_RADIUS = "jitter_radius"
        const val EXTRA_CANCELLED = "cancelled"
        private const val MIN_JITTER_RADIUS = 30
        private const val MAX_JITTER_RADIUS = 200
        private const val DEFAULT_JITTER_RADIUS = 50

        fun start(context: Context) {
            context.startService(Intent(context, OverlaySetupService::class.java))
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var scopeRing: View
    private lateinit var scopeCenter: View
    private lateinit var hintText: TextView
    private lateinit var confirmButton: View
    private lateinit var cancelButton: View
    private var vibrator: Vibrator? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var scopeCenterX = 0f
    private var scopeCenterY = 0f
    private var currentJitterRadius = DEFAULT_JITTER_RADIUS
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialScopeX = 0f
    private var initialScopeY = 0f
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        scopeCenterX = screenWidth * 0.85f
        scopeCenterY = screenHeight * 0.80f
        createOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY
    override fun onDestroy() {
        super.onDestroy()
        try { windowManager.removeView(overlayView) } catch (e: Exception) {}
    }
    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun createOverlay() {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_scope, null)
        scopeRing = overlayView.findViewById(R.id.scope_ring)
        scopeCenter = overlayView.findViewById(R.id.scope_center)
        hintText = overlayView.findViewById(R.id.hint_text)
        confirmButton = overlayView.findViewById(R.id.btn_confirm)
        cancelButton = overlayView.findViewById(R.id.btn_cancel)

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                currentJitterRadius = (currentJitterRadius * detector.scaleFactor).toInt().coerceIn(MIN_JITTER_RADIUS, MAX_JITTER_RADIUS)
                updateScopeSize()
                vibrateLight()
                return true
            }
        })

        overlayView.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            if (scaleGestureDetector.isInProgress) return@setOnTouchListener true
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialScopeX = scopeCenterX
                    initialScopeY = scopeCenterY
                }
                MotionEvent.ACTION_MOVE -> {
                    scopeCenterX = (initialScopeX + event.rawX - initialTouchX).coerceIn(0f, screenWidth.toFloat())
                    scopeCenterY = (initialScopeY + event.rawY - initialTouchY).coerceIn(0f, screenHeight.toFloat())
                    updateScopePosition()
                    checkSnapPoints()
                }
            }
            true
        }
        confirmButton.setOnClickListener { vibrateConfirm(); sendResult(false); stopSelf() }
        cancelButton.setOnClickListener { sendResult(true); stopSelf() }
        windowManager.addView(overlayView, layoutParams)
        overlayView.post { updateScopePosition(); updateScopeSize() }
    }

    private fun updateScopePosition() {
        scopeCenter.x = scopeCenterX - scopeCenter.width / 2
        scopeCenter.y = scopeCenterY - scopeCenter.height / 2
        scopeRing.x = scopeCenterX - scopeRing.width / 2
        scopeRing.y = scopeCenterY - scopeRing.height / 2
        val xRatio = (scopeCenterX / screenWidth * 100).toInt()
        val yRatio = (scopeCenterY / screenHeight * 100).toInt()
        hintText.text = "位置: ${xRatio}%, ${yRatio}% | 范围: ${currentJitterRadius}px"
    }

    private fun updateScopeSize() {
        val ringSize = currentJitterRadius * 2 + dp(40)
        scopeRing.layoutParams = scopeRing.layoutParams.apply { width = ringSize; height = ringSize }
        scopeRing.requestLayout()
        updateScopePosition()
    }

    private var lastSnapTime = 0L
    private fun checkSnapPoints() {
        val now = System.currentTimeMillis()
        if (now - lastSnapTime < 200) return
        val ct = dp(30)
        val isNearCenter = kotlin.math.abs(scopeCenterX - screenWidth / 2) < ct && kotlin.math.abs(scopeCenterY - screenHeight / 2) < ct
        val et = dp(20)
        val isNearEdge = scopeCenterX < et || scopeCenterX > screenWidth - et || scopeCenterY < et || scopeCenterY > screenHeight - et
        if (isNearCenter || isNearEdge) { vibrateLight(); lastSnapTime = now }
    }

    private fun sendResult(cancelled: Boolean) {
        sendBroadcast(Intent(ACTION_SCOPE_RESULT).apply {
            putExtra(EXTRA_CANCELLED, cancelled)
            if (!cancelled) {
                putExtra(EXTRA_X_RATIO, scopeCenterX / screenWidth)
                putExtra(EXTRA_Y_RATIO, scopeCenterY / screenHeight)
                putExtra(EXTRA_JITTER_RADIUS, currentJitterRadius)
            }
        })
    }

    private fun vibrateLight() {
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) it.vibrate(VibrationEffect.createOneShot(10, 50))
            else @Suppress("DEPRECATION") it.vibrate(10)
        }
    }

    private fun vibrateConfirm() {
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) it.vibrate(VibrationEffect.createOneShot(50, 150))
            else @Suppress("DEPRECATION") it.vibrate(50)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
