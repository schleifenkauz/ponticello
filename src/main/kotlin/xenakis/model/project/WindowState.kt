package xenakis.model.project

import javafx.geometry.Dimension2D
import javafx.stage.Stage
import javafx.stage.Window
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import xenakis.model.registry.ObjectReference

@Serializable
sealed class WindowState {
    abstract val reference: Reference

    private var x: Double? = null
    private var y: Double? = null
    private var width: Double? = null
    private var height: Double? = null

    @Transient
    private var target: Window? = null

    open fun applyTo(window: Stage, defaultSize: Dimension2D?) {
        target = window
        x?.let(window::setX)
        y?.let(window::setY)
        if (width == null && height == null) {
            if (defaultSize != null) {
                window.width = defaultSize.width
                window.height = defaultSize.height
            } else {
                window.sizeToScene()
            }
        } else {
            width?.let(window::setWidth)
            height?.let(window::setHeight)
        }
    }

    open fun saveFromTarget() {
        val target = target ?: return
        x = target.x.takeIf { it.isFinite() }
        y = target.y.takeIf { it.isFinite() }
        width = target.width.takeIf { it.isFinite() }
        height = target.height.takeIf { it.isFinite() }
    }

    @Serializable
    sealed interface Reference {
        @Serializable
        data class ByTitle(val title: String) : Reference

        @Serializable
        data class ByDisplayedObject(val objectType: String, val ref: ObjectReference<*>) : Reference {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is ByDisplayedObject) return false
                if (objectType != other.objectType) return false
                return ref.getName() == other.ref.getName()
            }

            override fun hashCode(): Int {
                var result = objectType.hashCode()
                result = 31 * result + ref.getName().hashCode()
                return result
            }
        }
    }
}