package ponticello.ui.actions

import fxutils.actions.*
import fxutils.prompt.IntegerPrompt
import hextant.context.Context
import hextant.context.compoundEdit
import javafx.geometry.Orientation
import javafx.geometry.Side
import javafx.scene.robot.Robot
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import org.kordamp.ikonli.materialdesign2.MaterialDesignL
import org.kordamp.ikonli.materialdesign2.MaterialDesignV
import ponticello.impl.Logger
import ponticello.impl.copy
import ponticello.impl.times
import ponticello.impl.zero
import ponticello.model.live.LiveObjectRegistry
import ponticello.model.obj.InstrumentObject
import ponticello.model.obj.InstrumentReference
import ponticello.model.obj.NoInstrument
import ponticello.model.obj.ParameterizedObject
import ponticello.model.project.InlineControlsDisplay
import ponticello.model.project.UIState
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.*
import ponticello.ui.controls.MultiObjectControlPopup
import ponticello.ui.controls.RenamePrompt
import ponticello.ui.dock.AppLayout
import ponticello.ui.impl.showDialog
import ponticello.ui.registry.InstrumentRegistryPane
import ponticello.ui.registry.SimpleSearchableRegistryView
import ponticello.ui.score.*
import reaktive.value.binding.*
import reaktive.value.now
import reaktive.value.reactiveValue
import reaktive.value.toggle

object ScoreObjectActions {
    val multiObjectActions = collectActions {
        addObjectAction("Remove objects") {
            description("Remove the selected object instances")
            shortcut("Alt?+DELETE")
            icon(Material2AL.DELETE)
            executes { ctx, ev ->
                val selection = RectangleSelection.get()
                if (selection != null) {
                    RectangleSelection.clear()
                    val pane = selection.pane
                    val containedInstances =
                        pane.viewsInside(selection.bounds, mustBeContainedEntirely = ev.isAltDown())
                            .mapTo(mutableSetOf()) { it.instance }
                    pane.score.removeObjects(containedInstances, Score.RegistryOption.ASK_IF_NEEDED)
                } else {
                    for (view in ctx.selectedViews) {
                        val instance = view.instance
                        val score = instance.score ?: continue
                        score.removeObject(instance, option = Score.RegistryOption.ASK_IF_NEEDED)
                    }
                }
            }
        }
        addObjectAction("Toggle mute") {
            description("Toggle mute the selected object instances")
            shortcut("Alt?+M")
            applicableIf { ctx -> ctx.selectedViews.any { view -> view.instance.obj.affectsPlayback } }
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
        addObjectAction("Replace objects") {
            shortcut("Alt?+H")
            executes { ctx, _ ->
                val popup = SimpleSearchableRegistryView(ctx.context[ScoreObjectRegistry], "Add object instance")
                val anchor = Robot().mousePosition
                val obj = popup.showPopup(anchor, owner = null) ?: return@executes
                val instances = ctx.selectedInstances.toSet()
                ctx.context.compoundEdit("Replace objects") {
                    for (instance in instances) {
                        instance.replaceWith(obj, autoSelect = instances.size == 1)
                    }
                }
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
                val objects = ctx.selectedObjects.filterIsInstance<ParameterizedObject>()
                if (objects.size != ctx.selectedObjects.size) {
                    Logger.warn("Some selected objects are not sound processes", Logger.Category.Score)
                    return@executes
                }
                MultiObjectControlPopup.show(ctx.context, objects)
            }
        }
    }

    val singleObjectActions: Action.Collector<ObjectActionContext> = collectActions {
        addAction("Show detail pane") {
            shortcut("Ctrl+D")
            applicableOn { view -> !view.parentPane.isRoot(view.obj) }
            executeSingle { view, ev ->
                if (ev.isTargetTextInput && !ev.isAltDown()) return@executeSingle
                val pane = view.context[AppLayout].get<ScoreObjectDetailPane>()
                pane.updateContent(view)
                pane.setShowing(true)
            }
        }
        addObjectAction("Duplicate object") {
            shortcut("Alt?+D")
            icon(MaterialDesignC.CONTENT_DUPLICATE)
            executeSingle { view, ev ->
                if (ev.isTargetTextInput && !ev.isAltDown()) return@executeSingle
                view.context[ScoreObjectSelectionManager].deselectAll()
                val duplicator = view.context[ScoreObjectDuplicator]
                duplicator.enterDuplicateMode(view.obj, view, clone = false)
            }
        }
        addObjectAction("Clone object") {
            shortcut("Alt?+Shift?+C")
            icon(MaterialDesignC.CONTENT_DUPLICATE)
            executeSingle { view, ev ->
                if (ev.isTargetTextInput && !ev.isAltDown()) return@executeSingle
                view.context[ScoreObjectSelectionManager].deselectAll()
                val duplicator = view.context[ScoreObjectDuplicator]
                val newName = view.context[ScoreObjectRegistry].nameForClone(view.obj, ev) ?: return@executeSingle
                val clone = view.obj.clone(newName)
                duplicator.enterDuplicateMode(clone, view, clone = ev.isShiftDown())
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
            shortcut("Alt?+Shift?+X")
            applicableOn { view -> !view.parentPane.isRoot(view.obj) }
            executeSingle { view, ev ->
                if (ev.isTargetTextInput && !ev.isAltDown()) return@executeSingle
                val inst = view.instance
                inst.score?.removeObject(inst, Score.RegistryOption.KEEP_IN_REGISTRY)
                val duplicator = view.context[ScoreObjectDuplicator]
                view.context[ScoreObjectSelectionManager].deselectAll()
                duplicator.enterDuplicateMode(inst.obj, view, clone = ev.isShiftDown())
            }
        }
        addObjectAction("Reverse object") {
            shortcut("Alt?+Shift?+R")
            icon(Material2AL.FLIP)
            applicableOn { view -> view is SoundProcessView }
            executeSingle { view, ev ->
                view as SoundProcessView
                if (!ev.isTargetTextInput || ev.isAltDown()) {
                    view.obj.reverse(reverseEnvelopes = ev.isShiftDown())
                }
            }
        }
        addObjectAction("View definition") {
            shortcut("Alt?+I")
            icon { ctx ->
                ctx.focusedView.map { v -> if (v is SoundProcessView) Codicons.CODE else MaterialDesignE.EYE }
            }
            applicableOn { v -> v is SoundProcessView || v is MidiObjectView || v is MidiNoteObjectView }
            ifNotApplicable(Action.IfNotApplicable.Disable)
            executeSingle { view, ev ->
                if (ev.isTargetTextInput && !ev.isAltDown()) {
                    return@executeSingle
                }
                when (view) {
                    is SoundProcessView -> showInstrumentDef(view.obj.def, view.context)
                    is MidiObjectView -> showInstrument(view.obj.instrument.now, view.context)
                    is MidiNoteObjectView -> showInstrument(view.obj.parentObject.instrument.now, view.context)
                }
            }
        }
        addObjectAction("Choose instrument") {
            shortcut("Shift+I")
            applicableIf { ctx -> ctx.selectedObjects.all { obj -> obj is ParameterizedObject } }
            executes { ctx, ev ->
                val commonInstrument = ctx.selectedObjects
                    .map { obj -> (obj as ParameterizedObject).def }
                    .singleOrNull()?.instrumentReference()
                val newInstrument = InstrumentSelectorPopup(ctx.context)
                    .showPopup(ev, initialOption = commonInstrument) ?: return@executes
                for (obj in ctx.selectedObjects) {
                    when (obj) {
                        is SoundProcess -> obj.instrumentRef.set(newInstrument)
                        is MidiObject -> obj.instrument.set(newInstrument)
                        is MidiNoteObject -> obj.parentObject.instrument.set(newInstrument)
                        else -> Logger.warn("Cannot set instrument for $obj", Logger.Category.Score)
                    }
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
        addObjectAction("Play object") {
            shortcut("Ctrl+SPACE")
            applicableOn { view -> view.obj.affectsPlayback }
            executeSingle { view, _ ->
                val obj = view.obj
                val registry = view.context[LiveObjectRegistry]
                val liveObject = registry.getOrCreateLiveScoreObject(obj)
                if (!liveObject.initialized) {
                    liveObject.inferQuantizationFrom(view.absolutePosition)
                    liveObject.absoluteScoreY.now = view.absolutePosition.y
                    if (ScoreObjectPlayerPane.hasPane(obj)) {
                        liveObject.playHead = ScoreObjectPlayerPane.getPane(obj).playHead
                    }
                }
                registry.add(liveObject)
                liveObject.toggle()
                if (liveObject.isActive.now) {
                    view.context[AppLayout].get<ScoreObjectViewPane>().showContent(view)
                }
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
        addObjectAction("Select parent") {
            shortcut("Alt+P")
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
//            icon(MaterialDesignS.SCISSORS_CUTTING)
            executeSingle { view, ev ->
                if (ev.isTargetTextInput && !ev.isAltDown()) return@executeSingle
                val orientation = if (ev.isShiftDown()) Orientation.VERTICAL else Orientation.HORIZONTAL
                view.cutObject(orientation)
            }
        }
    }

    private fun showInstrument(instrument: InstrumentReference, context: Context) {
        when (instrument) {
            InstrumentReference.None -> Logger.warn("No instrument", Logger.Category.Score)
            is InstrumentReference.UserDefined -> showInstrumentDef(instrument.reference.force(), context)
            is InstrumentReference.VST -> instrument.flow.force().showEditor()
        }
    }

    private fun showInstrumentDef(def: InstrumentObject, context: Context) {
        if (def is NoInstrument) {
            Logger.warn("Instrument is not resolved", Logger.Category.Score)
        } else {
            context[AppLayout].get<InstrumentRegistryPane>().showContent(def)
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