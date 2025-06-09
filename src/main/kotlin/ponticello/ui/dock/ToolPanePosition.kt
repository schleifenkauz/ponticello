package ponticello.ui.dock

import fxutils.SubWindow
import kotlinx.serialization.SerialName

sealed interface ToolPanePosition {
    @SerialName("Docket")
    data object Docked : ToolPanePosition

    @SerialName("Undocked")
    data class Undocked(
        val windowType: SubWindow.Type,
        val x: Double, val y: Double,
        val width: Double, val height: Double,
    ) : ToolPanePosition {
        companion object {
            fun center(type: SubWindow.Type = SubWindow.Type.ToolWindow) =
                Undocked(type, Double.NaN, Double.NaN, Double.NaN, Double.NaN)
        }
    }

    companion object {
        val docked get() = Docked
        val undocked get() = Undocked.center()
    }
}