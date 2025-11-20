package ponticello.ui.score

import fxutils.styleClass
import javafx.scene.shape.Polygon
import ponticello.model.score.controls.AttackReleaseControl
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.ParameterControlList
import ponticello.model.score.controls.ParameterControlList.NamedParameterControl
import reaktive.Observer
import reaktive.dependencies
import reaktive.value.now

class AttackReleaseOverlay(private val view: SoundProcessView) : ParameterControlList.Listener {
    private var observer: Observer? = null
    private var currentControl: AttackReleaseControl? = null
    private val overlayLeft = Polygon() styleClass "attack-release-overlay"
    private val overlayRight = Polygon() styleClass "attack-release-overlay"

    init {
//        overlayLeft.fill = Color.gray(0.5, 0.5)
//        overlayRight.fill = Color.gray(0.5, 0.5)
    }

    fun initialize() {
        view.obj.controls.addListener(this)
        updateOverlay()
    }

    private fun addedAttackReleaseControl(control: AttackReleaseControl) {
        observer?.kill()
        currentControl = control
        updateOverlay()
        observer = dependencies(control.attack, control.release).observe { updateOverlay() }
    }

    private fun removedAttackReleaseControl() {
        observer?.kill()
        currentControl = null
    }

    fun updateOverlay() {
        val ctrl = currentControl
        if (ctrl == null) {
            view.children.removeAll(overlayLeft, overlayRight)
        } else {
            if (overlayLeft !in view.children) view.children.add(overlayLeft)
            if (overlayRight !in view.children) view.children.add(overlayRight)
            overlayLeft.points.setAll(
                0.0, view.prefHeight, //bottom left
                0.0, 0.0, //top left
                view.getWidth(ctrl.attack.now), 0.0 //top right
            )

            overlayRight.points.setAll(
                view.prefWidth, view.prefHeight, //bottom right
                view.prefWidth, 0.0, //top right
                view.prefWidth - view.getWidth(ctrl.release.now), 0.0 //top left
            )
        }
    }

    override fun added(obj: NamedParameterControl, idx: Int) {
        val ctrl = obj.now
        if (ctrl is AttackReleaseControl) addedAttackReleaseControl(ctrl)
    }

    override fun removed(obj: NamedParameterControl, idx: Int) {
        val ctrl = obj.now
        if (ctrl is AttackReleaseControl) removedAttackReleaseControl()
    }

    override fun reassignedControl(
        parameter: NamedParameterControl, oldControl: ParameterControl, newControl: ParameterControl,
    ) {
        if (oldControl is AttackReleaseControl) removedAttackReleaseControl()
        if (newControl is AttackReleaseControl) addedAttackReleaseControl(newControl)
    }
}