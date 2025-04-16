package xenakis.model.obj

import kotlinx.serialization.Contextual
import xenakis.model.registry.ObjectReference
import xenakis.model.score.ScoreObject

typealias SynthDefReference = ObjectReference<@Contextual SynthDefObject>
typealias BusReference = ObjectReference<@Contextual BusObject>
typealias GroupReference = ObjectReference<@Contextual GroupObject>
typealias BufferReference = ObjectReference<@Contextual BufferObject>
typealias ProcessDefReference = ObjectReference<@Contextual ProcessDefObject>
typealias ScoreObjectReference = ObjectReference<@Contextual ScoreObject>