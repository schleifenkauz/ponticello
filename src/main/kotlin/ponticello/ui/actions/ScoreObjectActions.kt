package ponticello.ui.actions

import fxutils.actions.*
import fxutils.prompt.IntegerPrompt
import hextant.context.Context
import hextant.context.compoundEdit
import javafx.geometry.Orientation
import javafx.geometry.Side
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.*
import ponticello.impl.Logger
import ponticello.impl.copy
import ponticello.impl.times
import ponticello.impl.zero
import ponticello.model.obj.NoInstrument
import ponticello.model.project.InlineControlsDisplay
import ponticello.model.project.UIState
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.*
import ponticello.ui.controls.MultiObjectControlPopup
import ponticello.ui.controls.RenamePrompt
import ponticello.ui.dock.AppLayout
import ponticello.ui.impl.showDialog
import ponticello.ui.launcher.PonticelloMainActivity
import ponticello.ui.launcher.ScoreObjectDetailPane
import ponticello.ui.registry.InstrumentRegistryPane
import ponticello.ui.registry.ScoreObjectRegistryPane
import ponticello.ui.score.*
import reaktive.value.binding.*
import reaktive.value.reactiveValue
import reaktive.value.toggle

object ScoreObjectActions {
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
            applicableIf { ctx -> ctx.selectedViews.any { view -> view.instance.obj.affectsPlayback} }
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
        addObjectAction("Toggle inline controls") {
            shortcut("Alt?+L")
            icon { selector ->
                selector.focusedView
                    .flatMap { view -> view?.instance?.hideInlineControls ?: reactiveValue(false) }
                    .map { hidden ->
                        if (hidden) Material2AL.EXPAND_MORE
                        else Material2AL.EXPAND_LESS
                    }
            }
            enableWhen { ctx ->
                ctx.focusedView.map { v -> v !is ScoreObjectGroupView }
                    .and(ctx.context[UIState].controlsDisplay.notEqualTo(InlineControlsDisplay.CONTROLS_BAR))
            }
            ifNotApplicable(Action.IfNotApplicable.Hide)
            executeMultiAction { view, _ ->
                view.instance.hideInlineControls.toggle()
            }
        }
        addObjectAction("Copy selected objects to clipboard") {
            shortcut("Ctrl+C")
            executes { ctx, _ ->
                val selector = ctx.context[ScoreObjectSelectionManager]
                selector.setSystemClipboard(ctx.selectedViews.map { v -> v.instance })
            }
        }
        addObjectAction("Open Multi-Object edit popup") {
            shortcut("Alt?+O")
            executes { ctx, ev ->
                if (ev.isTargetTextInput && !ev.isAltDown()) return@executes
                if (ctx.selectedObjects.isEmpty()) {
                    Logger.info("No object selected", Logger.Category.Score)
                    return@executes
                }
                val soundProcesses = ctx.selectedObjects.filterIsInstance<SoundProcess>()
                if (soundProcesses.size != ctx.selectedObjects.size) {
                    Logger.warn("Some selected objects are not sound processes", Logger.Category.Score)
                    return@executes
                }
                MultiObjectControlPopup.show(ctx.context, soundProcesses)
            }
        }
    }

    val singleObjectActions = collectActions {
        addAction("Show detail pane") {
            shortcut("Alt?+D")
            applicableOn { view -> !view.parentPane.isRoot(view.obj) }
            executeSingle { view, ev ->
                if (ev.isTargetTextInput && !ev.isAltDown()) return@executeSingle
                val pane = view.context[AppLayout].get<ScoreObjectDetailPane>()
                pane.updateContent(view)
                pane.setShowing(true)
            }
        }
        addObjectAction("Show as sub window") {
            shortcut("Alt?+W")
            icon(MaterialDesignL.LAUNCH)
            applicableOn { view -> !view.parentPane.isRoot(view.obj) }
            executeSingle { view, ev ->
                if (ev.isTargetTextInput && !ev.isAltDown()) return@executeSingle
                val scoreObjectsPane = view.context[AppLayout].get<ScoreObjectRegistryPane>()
                val w = scoreObjectsPane.showContent(view.obj) ?: return@executeSingle
                val coords = view.localToScreen(view.width, 0.0)
                w.x = coords.x
                w.y = coords.y
            }
        }
        addObjectAction("Duplicate object") {
            shortcut("C")
            icon(MaterialDesignC.CONTENT_DUPLICATE)
            executeSingle { view, ev ->
                if (ev.isTargetTextInput) return@executeSingle
                view.context[ScoreObjectSelectionManager].deselectAll()
                val duplicator = view.context[ScoreObjectDuplicator]
                duplicator.enterDuplicateMode(view.obj, view)
            }
        }
        addObjectAction("Unlink from original") {
            shortcut("Alt?+U")
            icon(MaterialDesignL.LINK_OFF)
            enableWhen { ctx -> ctx.focusedView.map { focused -> focused == null || !focused.parentPane.isRoot(focused.obj) } }
            executes { ctx, ev ->
                if (ev.isTargetTextInput && !ev.isAltDown()) return@executes
                if (ctx.selectedInstances.isEmpty()) return@executes
                val obj = ctx.selectedObjects.singleOrNull()
                if (obj == null) {
                    Logger.warn("Cannot unlink from original: selected objects are not the same", Logger.Category.Score)
                    return@executes
                }
                val name = ctx.context[ScoreObjectRegistry].nameForClone(obj, null) ?: return@executes
                ctx.context.compoundEdit("Unlink from original") {
                    val clone = obj.clone(name)
                    for (oldInst in ctx.selectedInstances) {
                        val newInst = ScoreObjectInstance(clone, oldInst.position, oldInst.muted.copy())
                        oldInst.score?.addObject(newInst, autoSelect = true)
                        oldInst.score?.removeObject(oldInst, Score.RegistryOption.REMOVE_WITHOUT_ASKING)
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
            applicableOn { view -> view is SoundProcessView }
            executeSingle { view, ev ->
                view as SoundProcessView
                if (!ev.isTargetTextInput || ev.isAltDown()) {
                    view.obj.reverse()
                }
            }
        }
        addObjectAction("View definition") {
            shortcut("Alt?+I")
            applicableOn { v -> v is SoundProcessView }
            executeSingle { view, ev ->
                if (!ev.isTargetTextInput || ev.isAltDown()) {
                    view as SoundProcessView
                    val def = view.obj.instrument
                    if (def is NoInstrument) {
                        Logger.warn("Instrument is not resolved", Logger.Category.Score)
                        return@executeSingle
                    }
                    view.context[AppLayout].get<InstrumentRegistryPane>().showContent(def)
                }
            }
        }
        addObjectAction("Rename object") {
            shortcut("F2")
            executeSingle { view, _ -> RenamePrompt(view.obj, "New name for object").showDialog(view) }
        }
        addObjectAction("Add envelope") {
            shortcut("Alt?+E")
            applicableOn<SoundProcessView>()
            executeSingle { view, ev ->
                if (ev.isTargetTextInput && !ev.isAltDown()) return@executeSingle
                view as SoundProcessView
                view.showNewEnvelopePopup()
            }
        }
        addObjectAction("Infer quantization config from score") {
            icon(MaterialDesignM.METRONOME)
            applicableOn { view ->
                view.obj.affectsPlayback && view.scene?.window == view.context[PonticelloMainActivity].primaryStage
            }
            executeSingle { view, _ ->
                view.inferQuantization()
            }
        }

        addObjectAction("Extend object group") {
            shortcut("E")
            applicableOn<ScoreObjectGroupView>()
            executeSingle { view, ev ->
                if (ev.isTargetTextInput && !ev.isAltDown()) return@executeSingle
                val obj = view.obj as? ScoreObjectGroup ?: return@executeSingle
                val context = view.context
                extendGroup(obj, context, moreThanOne = false, cloneObjects = false)
            }
        }

        addObjectAction("Extend object group (Customized)") {
            shortcut("Alt+E")
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
            shortcut("Alt+Shift+E")
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
                        val duplicate = subInst.duplicate(inst.position + subInst.position)
                        parentScore.addObject(duplicate, autoSelect = false)
                    }
                    view.instance.score!!.removeObject(view.instance, Score.RegistryOption.REMOVE_WITHOUT_ASKING)
                }
            }
        }
        addObjectAction("Transpose") {
            icon(MaterialDesignP.PROGRESS_QUESTION)
            applicableOn<MidiObjectView>()
            executeSingle { view, _ ->
                view as MidiObjectView
                view.showTransposeDialog()
            }
        }
        addObjectAction("Select parent") {
            shortcut("Alt?+P")
            applicableOn<ScoreObjectView>()
            executeSingle { view, ev ->
                if (ev.isTargetTextInput && !ev.isAltDown()) return@executeSingle
                val parent = view.parentPane.parent ?: return@executeSingle
                if (parent is ScoreObjectView) {
                    view.context[ScoreObjectSelectionManager].select(parent, addToSelection = false)
                }
            }
        }
        addObjectAction("Slice object") {
            shortcut("Alt?+Shift?+COMMA")
            icon(MaterialDesignS.SCISSORS_CUTTING)
            executeSingle { view, ev ->
                if (ev.isTargetTextInput && !ev.isAltDown()) return@executeSingle
                val orientation = if (ev.isShiftDown()) Orientation.VERTICAL else Orientation.HORIZONTAL
                view.cutObject(orientation)
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
                ScoreObject.ResizeMode.Regular, Side.RIGHT
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