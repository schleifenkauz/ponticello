package xenakis.model.live

import kotlinx.serialization.Transient
import reaktive.value.ReactiveVariable
import reaktive.value.now
import xenakis.model.obj.ParameterizedObject
import xenakis.model.obj.ParameterizedObjectDef
import xenakis.model.obj.ProcessDefObject
import xenakis.model.obj.ProcessDefReference
import xenakis.model.player.ActiveLiveObject
import xenakis.model.player.ActiveObject
import xenakis.model.score.ParameterControlList
import xenakis.sc.client.ScWriter
import xenakis.sc.editor.ProcessDefSelector

class LiveProcessObject(
    override val mutableName: ReactiveVariable<String>,
    private val defRef: ReactiveVariable<ProcessDefReference>,
    override val controls: ParameterControlList,
) : LiveObject(), ParameterizedObject {
    override val superColliderName: String
        get() = "Tdef(\\${name.now})"

    override val superColliderPrefix: String
        get() = "Tdef"

    @Transient
    lateinit var processDefSelector: ProcessDefSelector
        private set

    val processDef get() = defRef.now.get() ?: ProcessDefObject.unresolved()

    override val def: ParameterizedObjectDef
        get() = processDef

    override fun activeObjects(): List<ActiveObject> = if (isActive.now) listOf(ActiveLiveObject(this)) else emptyList()

    override fun validate(): Boolean = processDefSelector.isResolved.now && controls.validate()

    override fun ScWriter.createObject() {
        TODO()
    }

    override fun doActivate() {
        client.run("$superColliderName.resume; $superColliderName.play")
    }

    override fun doDeactivate() {
        client.run("$superColliderName.pause")
    }

    override fun doReset() {
        client.run("$superColliderName.stop")
    }
}