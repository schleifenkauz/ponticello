package xenakis.model.live

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now
import xenakis.impl.Decimal
import xenakis.model.obj.NoSynthDef
import xenakis.model.obj.ParameterizedObject
import xenakis.model.obj.ParameterizedObjectDef
import xenakis.model.obj.SynthDefReference
import xenakis.model.player.ActiveObject
import xenakis.model.score.ParameterControlList
import xenakis.sc.editor.SynthDefSelector

@Serializable
class LiveSynthObject(
    private var defRef: ReactiveVariable<SynthDefReference>,
    override val controls: ParameterControlList,
) : ParameterizedObject, LiveObject {
    @Transient
    lateinit var synthDefSelector: SynthDefSelector
        private set

    val synthDef get() = defRef.now.get() ?: NoSynthDef()

    override val def: ParameterizedObjectDef
        get() = synthDef
    override val superColliderPrefix: String
        get() = TODO("Not yet implemented")

    override fun activeObjects(): List<ActiveObject> {
        TODO("Not yet implemented")
    }

    override fun duration(): ReactiveValue<Decimal>? {
        TODO("Not yet implemented")
    }

    override fun validate(): Boolean {
        TODO("Not yet implemented")
    }

    override val isAdded: ReactiveBoolean
        get() = TODO("Not yet implemented")
    override val name: ReactiveValue<String>
        get() = TODO("Not yet implemented")
    override val context: Context
        get() = TODO("Not yet implemented")

    override fun initialize(context: Context) {
        TODO("Not yet implemented")
    }
}