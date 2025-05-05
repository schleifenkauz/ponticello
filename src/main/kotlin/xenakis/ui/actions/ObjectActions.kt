package xenakis.ui.actions

import fxutils.actions.collectActions
import fxutils.actions.isAltDown
import fxutils.actions.isTargetTextInput
import fxutils.prompt.IntegerPrompt
import hextant.context.Context
import hextant.undo.compoundEdit
import javafx.geometry.HorizontalDirection.RIGHT
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.*
import reaktive.value.binding.Binding
import reaktive.value.binding.flatMap
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.impl.Logger
import xenakis.impl.copy
import xenakis.impl.times
import xenakis.impl.zero
import xenakis.model.obj.NoSynthDef
import xenakis.model.registry.ScoreObjectRegistry
import xenakis.model.score.*
import xenakis.ui.controls.RenamePrompt
import xenakis.ui.impl.Direction
import xenakis.ui.impl.showDialog
import xenakis.ui.launcher.DetailPaneManager
import xenakis.ui.launcher.XenakisMainActivity
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
                score.removeObject(instance, option = Score.RegistryOption.ASK_IF_NEEDED)
            }
        }
        addObjectAction("Toggle mute") {
            description("Toggle mute the selected object instances")
            shortcut("Alt?+M")
            icon { selector ->
                selector.focusedView
                    .flatMap { view -> view?.instance?.muted ?: reactiveValue(false) }
                    .map { muted ->
                        if (muted) MaterialDesignV.VOLUME_VARIANT_OFF
                        else MaterialDesignV.VOLUME_HIGH
                    }
            }
            executeMultiAction { view, ev ->
                if (!ev.isTargetTextInput || ev.isAltDown()) {
                    view.instance.toggleMuted()
                }
            }
        }
        addObjectAction("Copy selected objects to clipboard") {
            shortcut("Ctrl+C")
            executes { ctx, _ ->
                val selector = ctx.context[ScoreObjectSelectionManager]
                selector.setSystemClipboard(ctx.selectedViews.map { v -> v.instance })
            }
        }
    }

    val singleObjectActions = collectActions {
        addAction("Show detail pane") {
            shortcut("Alt?+D")
            applicableOn { view -> !view.parentPane.isRoot(view.obj) }
            executeSingle { view, _ ->
                view.context[DetailPaneManager].showDetailPane(view)
            }
        }
        addObjectAction("Show as sub window") {
            shortcut("Alt?+W")
            icon(MaterialDesignL.LAUNCH)
            applicableOn { view -> !view.parentPane.isRoot(view.obj) }
            executeSingle { view, _ ->
                val scoreObjectsPane = view.context[XenakisMainActivity].scoreObjectsPane
                val w = scoreObjectsPane.listView.showContent(view.obj) ?: return@executeSingle
                val coords = view.localToScreen(view.width, 0.0)
                w.x = coords.x
                w.y = coords.y
            }
        }
        addObjectAction("Duplicate object") {
            shortcut("Alt?+C")
            icon(MaterialDesignC.CONTENT_DUPLICATE)
            executeSingle { view, ev ->
                if (ev.isTargetTextInput && !ev.isAltDown()) return@executeSingle
                view.context[ScoreObjectSelectionManager].deselectAll()
                val duplicator = view.context[ScoreObjectDuplicator]
                duplicator.enterDuplicateMode(view.obj, view)
            }
        }
        addObjectAction("Unlink from original") {
            shortcut("Alt?+U")
            icon(MaterialDesignL.LINK_OFF)
            applicableOn { view -> !view.parentPane.isRoot(view.obj) }
            executes { ctx, ev ->
                if (ev.isTargetTextInput && !ev.isAltDown()) return@executes
                if (ctx.selectedViews.isEmpty()) return@executes
                ctx.context.compoundEdit("Unlink from original") {
                    for ((obj, instances) in ctx.selectedViews.map { v -> v.instance }.groupBy { inst -> inst.obj }) {
                        val name = ctx.context[ScoreObjectRegistry].nameForClone(obj)
                        val clone = obj.clone(name)
                        for (oldInst in instances) {
                            val newInst = ScoreObjectInstance(clone, oldInst.position, oldInst.muted.copy())
                            oldInst.score?.addObject(newInst, autoSelect = true)
                            oldInst.score?.removeObject(oldInst, Score.RegistryOption.REMOVE_WITHOUT_ASKING)
                        }
                    }
                }

            }
        }
        addObjectAction("Cut object") {
            shortcut("Alt?+X")
            applicableOn { view -> !view.parentPane.isRoot(view.obj) }
            executeSingle { view, ev ->
                if (ev.isTargetTextInput && !ev.isAltDown()) return@executeSingle
                val inst = view.instance
                inst.score?.removeObject(inst, Score.RegistryOption.KEEP_IN_REGISTRY)
                val duplicator = view.context[ScoreObjectDuplicator]
                view.context[ScoreObjectSelectionManager].deselectAll()
                duplicator.enterDuplicateMode(inst.obj, view)
            }
        }
        addObjectAction("Reverse object") {
            shortcut("Alt?+R")
            icon(Material2AL.FLIP)
            applicableOn { view -> view is SynthObjectView }
            executeSingle { view, ev ->
                view as SynthObjectView
                if (!ev.isTargetTextInput || ev.isAltDown()) {
                    view.obj.reverse()
                }
            }
        }
        addObjectAction("View definition") {
            shortcut("Alt?+I")
            applicableOn { v -> v is SynthObjectView || v is ProcessObjectView }
            executeSingle { view, ev ->
                if (!ev.isTargetTextInput || ev.isAltDown()) {
                    val mainScreen = view.context[XenakisMainActivity]
                    if (view is SynthObjectView) {
                        val def = view.obj.synthDef
                        if (def is NoSynthDef) {
                            Logger.warn("Instrument is not resolved", Logger.Category.Score)
                            return@executeSingle
                        }
                        mainScreen.synthDefsPane.listView.showContent(def)
                    } else if (view is ProcessObjectView) {
                        if (view.obj.processDefRef.now.isResolved.now) {
                            val obj = view.obj.processDef
                            mainScreen.processDefsPane.listView.showContent(obj)
                        } else {
                            Logger.warn("ProcessDef is not resolved", Logger.Category.Score)
                        }
                    }
                }
            }
        }
        addObjectAction("Rename object") {
            shortcut("F2")
            executeSingle { view, _ ->
                val obj = view.obj
                RenamePrompt(obj, "New name for object").showDialog(view.context)
            }
        }

        addObjectAction("Infer quantization config from score") {
            icon(MaterialDesignM.METRONOME)
            applicableOn { view ->
                view.obj.affectsPlayback && view.scene.window == view.context[XenakisMainActivity].primaryStage
            }
            executeSingle { view, _ ->
                view.inferQuantization()
            }
        }

        addObjectAction("Extend object group") {
            shortcut("E")
            applicableOn<ScoreObjectGroupView>()
            executeSingle { view, _ ->
                val obj = view.obj as? ScoreObjectGroup ?: return@executeSingle
                val context = view.context
                extendGroup(obj, context, moreThanOne = false, cloneObjects = false)
            }
        }

        addObjectAction("Extend object group (Customized)") {
            shortcut("Ctrl+E")
            applicableOn<ScoreObjectGroupView>()
            executeSingle { view, _ ->
                val obj = view.obj as? ScoreObjectGroup ?: return@executeSingle
                val context = view.context
                extendGroup(obj, context, moreThanOne = true, cloneObjects = false)
            }
        }
        addObjectAction("Extend object group (Clone children)") {
            shortcut("Shift+E")
            applicableOn<ScoreObjectGroupView>()
            executeSingle { view, _ ->
                val obj = view.obj as? ScoreObjectGroup ?: return@executeSingle
                val context = view.context
                extendGroup(obj, context, moreThanOne = false, cloneObjects = true)
            }
        }
        addObjectAction("Extend object group (Customized, clone children)") {
            shortcut("Ctrl+Shift+E")
            applicableOn<ScoreObjectGroupView>()
            executeSingle { view, _ ->
                val obj = view.obj as? ScoreObjectGroup ?: return@executeSingle
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
                        parentScore.addObject(subInst, autoSelect = false)
                    }
                    inst.score!!.removeObject(inst, Score.RegistryOption.REMOVE_WITHOUT_ASKING)
                }
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

    val all = collectActions {
        addAll(multiObjectActions)
        addAll(singleObjectActions)
    }

    private fun canApplyPlaybackActions(ctx: ObjectActionContext): Binding<Boolean> {
        return ctx.focusedView.flatMap { view ->
            if (view == null || !view.obj.affectsPlayback) reactiveValue(false)
            else reactiveValue(true)
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
                ScoreObject.ResizeMode.Regular, Direction.horizontal(RIGHT)
            )
            for (n in 1..times) {
                for (subInst in obj.score.objectInstances.toList()) {
                    val pos = subInst.position + ObjectPosition(duration * n, zero)
                    val newInst = if (cloneObjects) subInst.clone(pos) else subInst.duplicate(pos)
                    obj.score.addObject(newInst, autoSelect = false)
                }
            }
        }
    }
}