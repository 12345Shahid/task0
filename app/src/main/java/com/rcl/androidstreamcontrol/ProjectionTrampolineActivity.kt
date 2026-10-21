package com.rcl.androidstreamcontrol

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.rcl.androidstreamcontrol.webrtc.AgoraManager

class ProjectionTrampolineActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("Trampoline", "Invisible Trampoline Activity started successfully from background!")
        
        // This instantly triggers the MediaProjection popup since we are now legally in the foreground
        AgoraManager.isWebRTCActive = true
        AgoraManager.instance?.startScreenCaptureAndJoin()
        
        // Delay finishing so Agora's internal MediaProjection assistant activity has time to launch and show the system popup
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed) {
                finish()
            }
        }, 5000)
    }
}
