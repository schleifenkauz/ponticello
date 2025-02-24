package xenakis.ui.actions

import hextant.context.Context
import hextant.undo.compoundEdit
import javafx.geometry.HorizontalDirection.RIGHT
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import org.kordamp.ikonli.materialdesign2.MaterialDesignV
import reaktive.value.binding.flatMap
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.impl.times
import xenakis.impl.zero
import xenakis.model.registry.ScoreObjectRegistry
import xenakis.model.score.ObjectPosition
import xenakis.model.score.ScoreObject
import xenakis.model.score.ScoreObjectGroup
import xenakis.ui.impl.Direction
import xenakis.ui.impl.SubWindow
import xenakis.ui.launcher.XenakisMainActivity
import xenakis.ui.launcher.XenakisMainActivity.Mode
import xenakis.ui.prompt.IntegerPrompt
import xenakis.ui.prompt.NamePrompt
import xenakis.ui.score.*

object ObjectActions {
    val multiObjectActions = collectActions {
        addObjectAction("Remove objects") {
            description("Remove the selected object instances")
            shortcut("DELETE")
            icon(Material2AL.DELETE)
            executeMultiAction { view, _ ->
                val instance = view.instance
                val score = instance.score ?: return@executeMultiAction
                score.removeObject(instance)
            }
        }
        addObjectAction("Toggle mute") {
            description("Toggle mute the selected object instances")
            shortcut("M")
            icon { selector ->
                selector.focusedView
                    .flatMap { view -> view?.instance?.muted ?: reactiveValue(false) }
                    .map { muted ->
                        if (muted) MaterialDesignV.VOLUME_VARIANT_OFF
                        else MaterialDesignV.VOLUME_HIGH
                    }
            }
            executeMultiAction { view, _ ->
                if (view !is MemoObjectView && view !is TempoGridObjectView) {
                    view.instance.toggleMuted()
                }
            }
        }
    }

    val singleObjectActions = collectActions {
        addObjectAction("Create loop") {
            shortcut("L")
            icon(MaterialDesignR.REPEAT)
            executeSingle { view, ev ->
                if (!ev.isTargetTextInput) {
                    val config = view.askForLoopConfig() ?: return@executeSingle
                    val instance = view.instance
                    val score = instance.score ?: return@executeSingle
                    score.loop(instance, config.period, config.repetitions)
                }
            }
        }
        addObjectAction("Reverse object") {
            shortcut("R")
            icon(Material2AL.FLIP)
            applicableOn<SynthObjectView>()
            executeSingle { view, ev ->
                view as SynthObjectView
                if (!ev.isTargetTextInput) {
                    view.obj.reverse()
                }
            }
        }
        addObjectAction("View Instrument") {
            shortcut("I")
            applicableOn { v -> v is SynthObjectView || v is ProcessObjectView }
            executeSingle { view, ev ->
                if (!ev.isTargetTextInput) {
                    val mainScreen = view.context[XenakisMainActivity]
                    if (view is SynthObjectView) {
                        mainScreen.instrumentsPane.editInstrument(view.obj.synthDef)
                    } else if (view is ProcessObjectView) {
                        mainScreen.processDefsPane.editProcessDef(view.obj.processDef)
                    }
                }
            }
        }
        addObjectAction("Rename object") {
            shortcut("F2")
            executeSingle { view, _ ->
                val obj = view.instance.obj
                val name = NamePrompt(view.context[ScoreObjectRegistry], "New name for object", obj.name.now)
                    .showDialog(view.context) ?: return@executeSingle
                obj.rename(name)
            }
        }

        addObjectAction("Extend object group") {
            shortcut("E")
            applicableOn<ScoreObjectGroupView>()
            executeSingle { view, _ ->
                val obj = view.instance.obj as? ScoreObjectGroup ?: return@executeSingle
                val context = view.context
                extendGroup(obj, context, moreThanOne = false, cloneObjects = false)
            }
        }

        addObjectAction("Extend object group (Customized)") {
            shortcut("Ctrl+E")
            applicableOn<ScoreObjectGroupView>()
            executeSingle { view, _ ->
                val obj = view.instance.obj as? ScoreObjectGroup ?: return@executeSingle
                val context = view.context
                extendGroup(obj, context, moreThanOne = true, cloneObjects = false)
            }
        }
        addObjectAction("Extend object group (Clone children)") {
            shortcut("Shift+E")
            applicableOn<ScoreObjectGroupView>()
            executeSingle { view, _ ->
                val obj = view.instance.obj as? ScoreObjectGroup ?: return@executeSingle
                val context = view.context
                extendGroup(obj, context, moreThanOne = false, cloneObjects = true)
            }
        }
        addObjectAction("Extend object group (Customized, clone children)") {
            shortcut("Ctrl+Shift+E")
            applicableOn<ScoreObjectGroupView>()
            executeSingle { view, _ ->
                val obj = view.instance.obj as? ScoreObjectGroup ?: return@executeSingle
                val context = view.context
                extendGroup(obj, context, moreThanOne = true, cloneObjects = false)
            }
        }

        addObjectAction("Ungroup objects") {
            description("Move objects to parent score")
            shortcut("Shift+DELETE")
            executeSingle { view, _ ->
                val inst = view.instance
                val obj = inst.obj as? ScoreObjectGroup ?: return@executeSingle
                val parentScore = inst.score!!
                view.context.compoundEdit("Move objects to parent score") {
                    for (subInst in obj.score.objectInstances.toList()) {
                        subInst.moveTo(inst.position + subInst.position)
                        parentScore.addObject(subInst)
                    }
                    inst.score!!.removeObject(inst)
                }
            }
        }
        addObjectAction("Edit object properties") {
            shortcut("P")
            icon(Material2MZ.TUNE)
            applicableIf { ctx ->
                if (ctx.context[XenakisMainActivity].mode == Mode.Laptop) ctx.focusedView.map { v -> v != null }
                else reactiveValue(false)
            }
            executeSingle { view, _ ->
                val pane = view.getDetailPane()
                val name = view.instance.obj.name.now
                val window = SubWindow(pane, "Configure $name", view.context, type = SubWindow.Type.Undecorated)
                window.show()
            }
        }
        addObjectAction("Transpose") {
            icon(MaterialDesignP.PROGRESS_QUESTION)
            applicableOn<PianoRollObjectView>()
            executeSingle { view, _ ->
                view as PianoRollObjectView
                view.showTransposeDialog()
            }
        }
    }

    private fun extendGroup(obj: ScoreObjectGroup, context: Context, moreThanOne: Boolean, cloneObjects: Boolean) {
        val times =
            if (moreThanOne) IntegerPrompt("Loop count", 1, 1..16).showDialog(context) ?: return
            else 1
        context.compoundEdit("Extend object group") {
            val duration = obj.duration
            obj.resize(
                duration * (times + 1), obj.height,
                ScoreObject.ResizeType.Regular, Direction.horizontal(RIGHT)
            )
            for (n in 1..times) {
                for (subInst in obj.score.objectInstances.toList()) {
                    val pos = subInst.position + ObjectPosition(duration * n, zero)
                    val newInst = if (cloneObjects) subInst.clone(pos) else subInst.duplicate(pos)
                    obj.score.addObject(newInst)
                }
            }
        }
    }
}