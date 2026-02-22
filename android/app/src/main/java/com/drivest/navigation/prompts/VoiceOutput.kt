package com.drivest.navigation.prompts

interface VoiceOutput {
    fun speak(text: String)
    fun stop()
}
