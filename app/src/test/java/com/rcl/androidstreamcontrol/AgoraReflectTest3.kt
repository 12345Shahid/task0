package com.rcl.androidstreamcontrol

import org.junit.Test
import io.agora.rtc2.RtcEngine
import android.content.Intent
import android.media.projection.MediaProjection

class AgoraReflectTest3 {
    @Test
    fun reflect() {
        println("=== Methods taking Intent ===")
        RtcEngine::class.java.methods.filter { m -> m.parameterTypes.any { it == Intent::class.java } }.forEach {
            println(it.name + "(" + it.parameterTypes.joinToString { p -> p.simpleName } + ")")
        }
        println("=== Methods taking MediaProjection ===")
        RtcEngine::class.java.methods.filter { m -> m.parameterTypes.any { it == MediaProjection::class.java } }.forEach {
            println(it.name + "(" + it.parameterTypes.joinToString { p -> p.simpleName } + ")")
        }
    }
}
