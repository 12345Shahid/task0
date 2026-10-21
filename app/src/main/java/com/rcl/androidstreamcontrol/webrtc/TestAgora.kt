package com.rcl.androidstreamcontrol.webrtc
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.ScreenCaptureParameters
import java.lang.reflect.Method

fun main() {
    val methods = RtcEngine::class.java.methods.filter { it.name.contains("ScreenCapture") }
    for (m in methods) {
        println("RtcEngine method: " + m.name + "(" + m.parameterTypes.joinToString { it.simpleName } + ")")
    }
}
