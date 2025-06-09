package ponticello.model.obj

import kotlinx.serialization.Contextual
import ponticello.model.flow.AudioFlowGroup
import ponticello.model.player.ClockObject
import ponticello.model.registry.ObjectReference
import ponticello.model.score.ScoreObject

typealias InstrumentReference = ObjectReference<@Contextual InstrumentObject>
typealias BusReference = ObjectReference<@Contextual BusObject>
typealias BufferReference = ObjectReference<@Contextual BufferObject>
typealias GlobalPatternReference = ObjectReference<@Contextual GlobalPatternObject>
typealias ScoreObjectReference = ObjectReference<@Contextual ScoreObject>
typealias MeterReference = ObjectReference<@Contextual MeterObject>
typealias ClockReference = ObjectReference<@Contextual ClockObject>
typealias ParameterDefReference = ObjectReference<@Contextual ParameterDefObject>
typealias FlowGroupReference = ObjectReference<@Contextual AudioFlowGroup>