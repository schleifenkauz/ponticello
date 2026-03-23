package ponticello.model.live

import fxutils.undo.AbstractEdit
import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.copy
import ponticello.model.midi.MidiGridInstrument
import ponticello.model.obj.AbstractContextualObject
import reaktive.Observer
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class GridItem(
    @SerialName("target") private val _target: ReactiveVariable<ItemTarget> = reactiveVariable(ItemTarget.None()),
    val mode: ReactiveVariable<Mode> = reactiveVariable(_target.now.supportedModes.first()),
) : AbstractContextualObject() {
    @Transient
    private lateinit var grid: MidiGridInstrument

    @Transient
    private lateinit var modeObserver: Observer

    var target: ItemTarget
        get() = _target.now
        set(value) {
            val oldTarget = _target.now
            if (value == oldTarget) return
            _target.now = value
            var oldMode: Mode? = null
            if (mode.now !in value.supportedModes) {
                oldMode = mode.now
                mode.now = value.supportedModes.first()
            }
            value.initialize(grid)
            grid.updatedItem(this, value)
            grid.undoManager.record(ChooseTarget(this, oldTarget, value, oldMode))
            grid.notifyViews { updateItem(this@GridItem) }
        }

    fun target(): ReactiveValue<ItemTarget> = _target

    fun reference() = grid.getReference(this)

    override fun initialize(context: Context) {
        super.initialize(context)
        target.initialize(grid)
    }

    fun initialize(context: Context, grid: MidiGridInstrument) {
        this.grid = grid
        initialize(context)
        modeObserver = mode.observe { _, _, newMode ->
            grid.updatedMode(this, newMode)
        }
    }

    fun copy(): GridItem = GridItem(reactiveVariable(target.copy()), mode.copy())

    enum class Mode {
        None, Trigger, Gate, Toggle;
    }

    private class ChooseTarget(
        private val item: GridItem,
        private val oldTarget: ItemTarget, private val newTarget: ItemTarget,
        private val oldMode: Mode?
    ) : AbstractEdit() {
        override val actionDescription: String
            get() = if (newTarget is ItemTarget.None) "Reset grid item target" else "Choose grid item target"

        override fun doRedo() {
            item.target = newTarget
        }

        override fun doUndo() {
            item.target = oldTarget
            if (oldMode != null) item.mode.now = oldMode
        }
    }

    companion object {
        fun createDefault(): GridItem = GridItem()
    }
}