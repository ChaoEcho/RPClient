package me.kafuuneko.rpclient.libs.tts

data class MimoVoiceOption(
    val id: String,
    val label: String
)

val MIMO_VOICES = listOf(
    MimoVoiceOption("mimo_default", "MiMo 默认"),
    MimoVoiceOption("冰糖", "冰糖"),
    MimoVoiceOption("茉莉", "茉莉"),
    MimoVoiceOption("苏打", "苏打"),
    MimoVoiceOption("白桦", "白桦"),
    MimoVoiceOption("Mia", "Mia"),
    MimoVoiceOption("Chloe", "Chloe"),
    MimoVoiceOption("Milo", "Milo"),
    MimoVoiceOption("Dean", "Dean")
)
