package xenakis.ui.actions

import hextant.context.Context
import hextant.undo.UndoManager
import hextant.undo.compoundEdit
import javafx.geometry.HorizontalDirection.RIGHT
import javafx.scene.Node
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.material2.Material2OutlinedAL
import org.kordamp.ikonli.materialdesign2.*
import reaktive.value.binding.flatMap
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.impl.asTime
import xenakis.impl.times
import xenakis.impl.zero
import xenakis.model.InteractionSettings
import xenakis.model.Logger
import xenakis.model.XenakisProject
import xenakis.model.obj.BusObject
import xenakis.model.player.PlaybackManager
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.ScoreObjectRegistry
import xenakis.model.score.ObjectPosition
import xenakis.model.score.ScoreObject
import xenakis.model.score.ScoreObjectGroup
import xenakis.sc.Rate
import xenakis.ui.impl.Direction
import xenakis.ui.impl.NotificationView
import xenakis.ui.impl.SubWindow
import xenakis.ui.launcher.XenakisLauncher
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject
import xenakis.ui.launcher.XenakisMainScreen
import xenakis.ui.launcher.XenakisMainScreen.Mode
import xenakis.ui.misc.HelpBrowser
import xenakis.ui.misc.ServerOptionsPane
import xenakis.ui.prompt.IntegerPrompt
import xenakis.ui.prompt.NamePrompt
import xenakis.ui.registry.SearchableBusListView
import xenakis.ui.registry.SimpleSearchableListView
import xenakis.ui.score.*

object Actions {
    val fileActions = collectActions {
        addAction("Save Project") {
            icon(Material2MZ.SAVE)
            shortcut("Ctrl+S")
            execute { launcher: XenakisLauncher -> launcher.saveProject() }
        }
        addAction("Open Project") {
            icon(Codicons.FOLDER_OPENED)
            shortcut("Ctrl+O")
            execute { launcher: XenakisLauncher -> launcher.openProject() }
        }
        addAction("Create New Project") {
            icon(Material2OutlinedAL.CREATE_NEW_FOLDER)
            shortcut("Ctrl+N")
            execute { launcher: XenakisLauncher -> launcher.createNewProject() }
        }
        addAction("Close Project") {
            icon(MaterialDesignC.CLOSE)
            description("Close project and open the launcher window.")
            execute { launcher: XenakisLauncher -> launcher.closeProject() }
        }
    }

    val playbackActions = collectActions<PlaybackManager> {
        addAction("Toggle Playback") {
            shortcut("Ctrl+SPACE")
            icon { playback ->
                playback.player.isPlaying.map { playing ->
                    if (playing) Material2MZ.PAUSE
                    else Material2MZ.PLAY_ARROW
                }
            }
            execute { playback ->
                if (!playback.player.isPlaying.now) {
                    playback.player.play()
                } else {
                    playback.player.pause()
                }
            }
        }
        addAction("Stop") {
            description("Stop playback and free all Synths")
            shortcut("Ctrl+PERIOD")
            icon(Material2MZ.STOP)
            execute { playback -> playback.player.reset() }
        }
        addAction("Go to start") {
            description("Move the playback cursor to the start of the score")
            shortcut("Ctrl+DIGIT0")
            icon(Material2MZ.SKIP_PREVIOUS)
            execute { playback -> playback.movePlayHeadToStart() }
        }
        addAction("Toggle recording") {
            shortcut("Ctrl+R")
            icon { playback ->
                playback.recorder.isActive.map { active ->
                    if (active) MaterialDesignM.MICROPHONE
                    else MaterialDesignM.MICROPHONE_OUTLINE
                }
            }
            execute { playback, ev ->
                if (ev.isShiftDown()) {
                    val context = playback.context
                    val project = context[currentProject]
                    val currentSelected =
                        project.serverOptions.recordedBus?.get<BusObject>() ?: context[BusRegistry].getDefault()
                    SearchableBusListView(context[BusRegistry], "Select bus to record to", rate = Rate.Audio).showPopup(
                        context,
                        anchorNode = ev?.source as Node,
                        initialOption = currentSelected
                    ) { bus -> project.serverOptions.recordedBus = bus.createReference() }
                } else playback.recorder.toggleIsActive()
            }
        }
    }

    val scoreNavigationActions = collectActions<ScoreView> {
        addAction("Move View To Start") {
            description("Moves the displayed portion of the score to the start")
            shortcut("HOME")
            execute { view -> view.display(0.0.asTime, view.displayedDuration) }
        }
        addAction("Display Whole Score") {
            shortcut("HOME")
            execute { view -> view.displayWholeScore() }
        }
        addAction("Move to Cursor") {
            description("Move displayed portion of the score to playback cursor")
            shortcut("Shift+SPACE")
            execute { view ->
                val playback = view.context[PlaybackManager]
                val t = playback.playHead.currentTime
                view.display(t, view.displayedDuration + t)
            }
        }
        addAction("Move playback cursor to start") {
            shortcut("Ctrl+Shift?+DIGIT0")
            icon(Material2MZ.SKIP_PREVIOUS)
            execute { view, ev ->
                val playback = view.context[PlaybackManager]
                if (ev.isShiftDown()) {
                    if (playback.player.isPlaying.now) return@execute
                    view.display(0.0.asTime, view.displayedDuration)
                    playback.attachToMainScore()
                }
                playback.movePlayHeadToStart()
            }
        }
    }

    val serverActions = collectActions<XenakisProject> {
        addAction("Reboot server") {
            shortcut("Shift?+F5")
            icon(MaterialDesignR.RESTART)
            execute { project, ev ->
                if (ev.isShiftDown()) {
                    project.rebootServer()
                } else {
                    ServerOptionsPane(project.context, project.serverOptions).showDialog(project.context)
                }
            }
        }
        addAction("Sync with SuperCollider") {
            shortcut("Ctrl+Shift+S")
            execute { project -> project.syncWithSuperCollider() }
        }
        addAction("Plot Server Tree") {
            shortcut("Ctrl+Alt+T")
            execute { project -> project.client.run("s.plotTree") }
        }
        addAction("Monitor output") {
            shortcut("Ctrl+Shift+M")
            execute { project -> project.client.run("s.scope") }
        }
        addAction("Show ServerMeter") {
            shortcut("Ctrl+M")
            execute { project ->
                val numIns = project.serverOptions.numInputChannels
                val numOuts = project.serverOptions.numOutputChannels
                project.client.run("ServerMeter.new(s, $numIns, $numOuts)")
            }
        }
    }

    val windowActions = collectActions<XenakisMainScreen> {
        addAction("Open console") {
            shortcut("Ctrl+T")
            icon(MaterialDesignC.CONSOLE)
            execute { screen -> screen.shellWindow.show() }
        }
        addAction("Show log window") {
            shortcut("Ctrl+L")
            icon(MaterialDesignB.BELL)
            execute { screen, ev ->
                if (ev.isShiftDown()) {
                    SimpleSearchableListView(Logger.Level.entries, "Select notification level").showPopup(
                        screen.context,
                        ev?.source as Node,
                        NotificationView.level
                    ) { lvl ->
                        NotificationView.level = lvl
                    }
                } else screen.logWindow.show()
            }
        }
        addAction("Edit setup code") {
            icon(MaterialDesignF.FILE_COG)
            execute { screen, ev ->
                if (ev.isShiftDown()) screen.serverSetupCodeWindow.show()
                else screen.serverTreeCodeWindow.show()
            }
        }
        addAction("Open help browser") {
            shortcut("F1")
            icon(MaterialDesignW.WEB)
            execute { screen -> screen.context[HelpBrowser].show() }
        }
        addAction("Edit audio flow graph") {
            shortcut("Ctrl+Shift+F")
            icon(MaterialDesignG.GRAPH)
            execute { screen -> screen.flowGraphWindow.show() }
        }
        addAction("Edit settings") {
            shortcut("Ctrl+Alt+S")
            icon(MaterialDesignC.COG)
            execute { screen -> screen.settingsWindow.show() }
        }
        addAction("Show samples") {
            shortcut("Ctrl+F")
            icon(Material2AL.LIBRARY_MUSIC)
            execute { screen -> screen.samplesWindow.show() }
        }
        addAction("Show global controls") {
            shortcut("Ctrl+Shift+G")
            icon(Material2AL.GRAIN)
            execute { screen -> screen.globalControlsWindow.show() }
        }
        addAction("Show groups") {
            shortcut("Ctrl+G")
            icon(Material2AL.IMPORT_EXPORT)
            execute { screen -> screen.groupsWindow.show() }
        }
        addAction("Show buses") {
            shortcut("Ctrl+B")
            icon(Material2AL.GRAPHIC_EQ)
            execute { screen -> screen.busesWindow.show() }
        }
    }

    val undoRedoActions = collectActions<UndoManager> {
        addAction("Undo") {
            shortcut("Ctrl+Z")
            description(UndoManager::undoText)
            ifNotApplicable(Action.IfNotApplicable.Disable)
            icon(MaterialDesignU.UNDO)
            applicableIf { manager -> manager.canUndo }
        }
        addAction("Undo") {
            shortcut("Ctrl+Shift+Z")
            description(UndoManager::redoText)
            ifNotApplicable(Action.IfNotApplicable.Disable)
            icon(MaterialDesignR.REDO)
            applicableIf { manager -> manager.canRedo }
        }
    }

    val quitAction = collectActions<XenakisLauncher> {
        addAction("Quit") {
            shortcut("Ctrl+Shift?+Q")
            icon(Material2AL.CLOSE)
            execute { launcher, ev ->
                if (ev.isShiftDown()) {
                    launcher.saveProject()
                    launcher.quitApplication()
                } else launcher.closeRequest()
            }
        }
    }

    val interactionConfig = collectActions<InteractionSettings> {
        addAction("Toggle snapping") {
            shortcut("Q")
            icon { settings ->
                settings.snapEnabled.map { enabled ->
                    if (enabled) MaterialDesignM.MAGNET_ON //TODO why doesn't it update the icon?
                    else MaterialDesignM.MAGNET
                }
            }
            toggles { s -> s.snapEnabled }
        }
        addAction("Toggle time grid") {
            shortcut("T")
            icon(Material2AL.LINEAR_SCALE)
            toggles { s -> s.displayTimeGrid }
        }
    }

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
                    val mainScreen = view.context[XenakisMainScreen]
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
            shortcut("Ctrl+E")
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
                if (ctx.context[XenakisMainScreen].mode == Mode.Laptop) ctx.focusedView.map { v -> v != null }
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