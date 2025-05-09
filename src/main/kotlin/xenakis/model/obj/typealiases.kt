package xenakis.model.obj

import kotlinx.serialization.Contextual
import xenakis.model.player.ClockObject
import xenakis.model.registry.ObjectReference
import xenakis.model.score.ScoreObject

typealias SynthDefReference = ObjectReference<@Contextual SynthDefObject>
typealias BusReference = ObjectReference<@Contextual BusObject>
typealias BufferReference = ObjectReference<@Contextual BufferObject>
typealias ProcessDefReference = ObjectReference<@Contextual ProcessDefObject>
typealias GlobalPatternReference = ObjectReference<@Contextual GlobalPatternObject>
typealias ScoreObjectReference = ObjectReference<@Contextual ScoreObject>
typealias MeterReference = ObjectReference<@Contextual MeterObject>
typealias ClockReference = ObjectReference<@Contextual ClockObject>
typealias ParameterDefReference = ObjectReference<@Contextual ParameterDefObject>