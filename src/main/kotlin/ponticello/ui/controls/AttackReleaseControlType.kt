package ponticello.ui.controls

import fxutils.centerChildren
import fxutils.controls.SliderBar
import fxutils.undo.UndoManager
import javafx.scene.Node
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.scene.paint.Color
import ponticello.impl.one
import ponticello.impl.toDecimal
import ponticello.impl.zero
import ponticello.model.obj.ParameterizedObject
import ponticello.model.score.ParameterControlList
import ponticello.model.score.controls.AttackReleaseControl
import ponticello.model.score.controls.ParameterControl
import ponticello.sc.AttackReleaseControlSpec
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.sc.Warp
import ponticello.ui.score.ScoreObjectView
import reaktive.and
import reaktive.value.binding.orElse
import reaktive.value.forEach
import reaktive.value.now
import reaktive.value.reactiveValue

data object AttackReleaseControlType : ControlType<AttackReleaseControl>() {
    override fun applicableOn(obj: ParameterizedObject, spec: ControlSpec): Boolean =
        spec is NumericalControlSpec || spec is AttackReleaseControlSpec

    override fun createDetailInput(
        namedControl: ParameterControlList.NamedParameterControl,
        control: AttackReleaseControl,
        view: ScoreObjectView?,
    ): Node {
        val box = HBox(10.0)
        val spec = namedControl.spec.now as? AttackReleaseControlSpec
        val customTotalDuration = spec?.maxDuration ?: reactiveValue(null)
        val objectDuration = namedControl.parentObject.duration()
        val duration = customTotalDuration.orElse(objectDuration ?: reactiveValue(one))
        box.userData = duration.forEach { maxDur ->
            val timeSpec = NumericalControlSpec(
                default = zero, min = zero, max = maxDur,
                step = 0.01.toDecimal(), lag = zero, warp = Warp.Linear, associatedColor = Color.GRAY
            ).converter(unit = "s")
            control.attack.now = control.attack.now.coerceAtMost(maxDur)
            if (objectDuration != null) {
                control.release.now = control.release.now.coerceAtMost(maxDur - control.attack.now)
            } else {
                control.release.now = control.release.now.coerceAtMost(maxDur)
            }
            val attack =
                SliderBar(
                    control.attack, "Attack", timeSpec,
                    undoManager = namedControl.context[UndoManager]
                )
            val release = SliderBar(
                control.release, "Release", timeSpec,
                undoManager = namedControl.context[UndoManager]
            )
            attack.prefWidth = 100.0
            release.prefWidth = 100.0
            box.children.setAll(attack, release) //TODO would be awesome to merge them into one slider
        } and control.attack.observe { _, _, attack ->
            if (objectDuration != null) {
                control.release.now = control.release.now.coerceAtMost(duration.now - attack)
            }
        } and control.release.observe { _, _, release ->
            if (objectDuration != null) {
                control.attack.now = control.attack.now.coerceAtMost(duration.now - release)
            }
        }
        return box.centerChildren()
    }

    override fun createInitialControl(
        obj: ParameterizedObject,
        spec: ControlSpec?,
        oldControl: ParameterControl,
        namedControl: ParameterControlList.NamedParameterControl,
        anchorNode: Region,
    ): AttackReleaseControl = AttackReleaseControl.createDefault()

    override fun toString(): String = "ASR"
}