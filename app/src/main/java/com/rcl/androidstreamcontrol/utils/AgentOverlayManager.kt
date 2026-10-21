package com.rcl.androidstreamcontrol.utils

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

object AgentOverlayManager {
    private var overlayView: FrameLayout? = null
    private var windowManager: WindowManager? = null
    private var isAgentEnabled = false

    fun enableAgentControl(context: Context) {
        if (isAgentEnabled) return
        isAgentEnabled = true

        val handler = Handler(Looper.getMainLooper())
        handler.post {
            try {
                windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
                    else 
                        WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                )

                // Removed screenBrightness = 0.01f because it darkens the composition on emulators, causing MediaProjection to capture a black screen!

                overlayView = FrameLayout(context).apply {
                    // Set to 1% opacity black instead of Color.TRANSPARENT!
                    // Android 12+ security ignores touches on 100% transparent overlays to prevent tapjacking.
                    // By making it 1% opaque, it's invisible to the eye but allows us to actually block physical touches!
                    setBackgroundColor(Color.parseColor("#03000000"))
                    isClickable = true
                    
                    val textView = TextView(context).apply {
                        text = "Agent working - controls disabled"
                        setTextColor(Color.WHITE)
                        textSize = 18f
                        gravity = Gravity.CENTER
                        setBackgroundColor(Color.parseColor("#90000000")) 
                        setPadding(40, 20, 40, 20)
                    }
                    
                    addView(textView, FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                    ).apply {
                        bottomMargin = 150
                    })

                    // Consume all physical touches to disable "normal control"
                    setOnTouchListener { _, _ -> true }
                }

                windowManager?.addView(overlayView, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun disableAgentControl() {
        if (!isAgentEnabled) return
        isAgentEnabled = false

        Handler(Looper.getMainLooper()).post {
            try {
                overlayView?.let { windowManager?.removeView(it) }
                overlayView = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun temporarilyAllowTouches(callback: Runnable) {
        if (!isAgentEnabled || overlayView == null) {
            callback.run()
            return
        }
        
        Handler(Looper.getMainLooper()).post {
            try {
                val params = overlayView?.layoutParams as? WindowManager.LayoutParams
                if (params != null) {
                    params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    windowManager?.updateViewLayout(overlayView, params)
                }
            } catch (e: Exception) { e.printStackTrace() }
            
            // Wait briefly for WindowManager to apply the flag before executing the callback
            Handler(Looper.getMainLooper()).postDelayed({
                callback.run()
            }, 50)
        }
    }

    fun restoreTouchBlocking() {
        if (!isAgentEnabled || overlayView == null) return
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val params = overlayView?.layoutParams as? WindowManager.LayoutParams
                if (params != null) {
                    params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                    windowManager?.updateViewLayout(overlayView, params)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }, 150)
    }
}
