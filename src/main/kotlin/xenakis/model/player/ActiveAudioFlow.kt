package xenakis.model.player

import xenakis.model.flow.AudioFlow
import xenakis.model.obj.ParameterizedObject

class ActiveAudioFlow(val flow: AudioFlow) : ActiveObject() {
    override val associatedObject: ParameterizedObject?
        get() = flow as? ParameterizedObject

    override val superColliderName: String
        get() = flow.superColliderName

    override val uniqueName: String
        get() = superColliderName.removePrefix("~")
}