package com.di2media.mapping

sealed interface ButtonAction {
    val label: String
}

enum class InstantAction(override val label: String) : ButtonAction {
    NEXT("Next Track"),
    PREVIOUS("Previous Track"),
    PLAY_PAUSE("Play/Pause"),
    VOLUME_UP("Volume Up"),
    VOLUME_DOWN("Volume Down"),
    NONE("None"),
}

enum class HoldAction(override val label: String) : ButtonAction {
    VOLUME_RAMP_UP("Volume Ramp Up"),
    VOLUME_RAMP_DOWN("Volume Ramp Down"),
    PLAY_PAUSE("Play/Pause"),
    NEXT("Next Track"),
    PREVIOUS("Previous Track"),
    NONE("None"),
}
