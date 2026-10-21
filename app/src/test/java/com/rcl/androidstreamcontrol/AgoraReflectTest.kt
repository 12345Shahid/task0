package com.rcl.androidstreamcontrol

import org.junit.Test
import io.agora.rtc2.ScreenCaptureParameters
import io.agora.rtc2.RtcEngine

class AgoraReflectTest {
    @Test
    fun reflect() {
        println("=== ScreenCaptureParameters Fields ===")
        ScreenCaptureParameters::class.java.fields.forEach { println(it.name) }
        println("=== RtcEngine ScreenCapture Methods ===")
        RtcEngine::class.java.methods.filter { it.name.contains("ScreenCapture") }.forEach {
            println(it.name + "(" + it.parameterTypes.joinToString { p -> p.simpleName } + ")")
        }
    }
}
