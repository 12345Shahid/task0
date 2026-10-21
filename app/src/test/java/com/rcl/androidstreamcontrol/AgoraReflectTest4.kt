package com.rcl.androidstreamcontrol

import org.junit.Test
import io.agora.rtc2.ScreenCaptureParameters

class AgoraReflectTest4 {
    @Test
    fun reflect() {
        ScreenCaptureParameters::class.java.methods.forEach {
            println("Method: " + it.name)
        }
    }
}
