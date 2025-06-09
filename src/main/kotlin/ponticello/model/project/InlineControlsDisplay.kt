package ponticello.model.project

import kotlinx.serialization.Serializable

@Serializable
enum class InlineControlsDisplay {
    NONE, MINIMAL_OVERLAY, EXTENDED_OVERLAY, CONTROLS_BAR;

    override fun toString(): String = when (this) {
        NONE -> "None"
        MINIMAL_OVERLAY -> "Minimal Overlay"
        EXTENDED_OVERLAY -> "Extended Overlay"
        CONTROLS_BAR -> "Controls Bar"
    }
}