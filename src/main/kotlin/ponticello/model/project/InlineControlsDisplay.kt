package ponticello.model.project

import kotlinx.serialization.Serializable

@Serializable
enum class InlineControlsDisplay {
    NONE, MINIMAL_OVERLAY, EXTENDED_OVERLAY, CONTROLS_BAR
}