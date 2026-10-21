package com.rcl.androidstreamcontrol

import org.junit.Test
import io.agora.rtc2.RtcEngine

class AgoraReflectTest2 {
    @Test
    fun reflect() {
        RtcEngine::class.java.methods.filter { it.name.contains("External") || it.name.contains("MediaProjection") }.forEach {
            println(it.name + "(" + it.parameterTypes.joinToString { p -> p.simpleName } + ")")
        }
    }
}
