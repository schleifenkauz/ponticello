package xenakis.model.live

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveVariable
import reaktive.value.now
import xenakis.model.obj.NoSynthDef
import xenakis.model.obj.ParameterizedObject
import xenakis.model.obj.ParameterizedObjectDef
import xenakis.model.obj.SynthDefReference
import xenakis.model.player.ActiveLiveObject
import xenakis.model.player.ActiveObject
import xenakis.model.score.ParameterControlList
import xenakis.sc.client.ScWriter
import xenakis.sc.editor.SynthDefSelector

@Serializable
class LiveSynthObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    private var defRef: ReactiveVariable<SynthDefReference>,
    override val controls: ParameterControlList,
) : ParameterizedObject, LiveObject() {
    override val superColliderName: String
        get() = "Ndef(\\${name.now})"

    override val superColliderPrefix: String
        get() = "Ndef"

    @Transient
    lateinit var synthDefSelector: SynthDefSelector
        private set

    val synthDef get() = defRef.now.get() ?: NoSynthDef()

    override val def: ParameterizedObjectDef
        get() = synthDef

    override fun activeObjects(): List<ActiveObject> = if (isActive.now) listOf(ActiveLiveObject(this)) else emptyList()

    override fun doActivate() {

    }

    override fun doDeactivate() {
    }

    override fun doReset() {
    }

    override fun ScWriter.createObject() {
        TODO("Not yet implemented")
    }

    override fun validate(): Boolean = synthDefSelector.isResolved.now && controls.validate()
}