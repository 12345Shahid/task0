package com.rcl.androidstreamcontrol

import io.agora.rtc2.RtcEngine

class TestAgora {
    fun getMethods() {
        val methods = RtcEngine::class.java.methods.filter { it.name.contains("ScreenCapture") }
        for (m in methods) {
            println("RtcEngine method: " + m.name + "(" + m.parameterTypes.joinToString { it.simpleName } + ")")
        }
    }
}
