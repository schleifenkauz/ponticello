package ponticello.model.obj

import hextant.context.Context
import kotlinx.serialization.Contextual
import ponticello.model.flow.AudioFlow
import ponticello.model.flow.AudioFlowGroup
import ponticello.model.flow.VSTPluginFlow
import ponticello.model.live.LiveObject
import ponticello.model.live.LiveTaskObject
import ponticello.model.player.ClockObject
import ponticello.model.project.flows
import ponticello.model.record.LiveBufferObject
import ponticello.model.registry.ObjectReference
import ponticello.model.score.ScoreObject
import reaktive.value.now

typealias SynthDefReference = ObjectReference<@Contextual SynthDefObject>
typealias ProcessDefReference = ObjectReference<@Contextual ProcessDefObject>
typealias BusReference = ObjectReference<@Contextual BusObject>
typealias BufferReference = ObjectReference<@Contextual BufferObject>
typealias GlobalPatternReference = ObjectReference<@Contextual GlobalPatternObject>
typealias ScoreObjectReference = ObjectReference<@Contextual ScoreObject>
typealias MeterReference = ObjectReference<@Contextual MeterObject>
typealias ClockReference = ObjectReference<@Contextual ClockObject>
typealias ParameterDefReference = ObjectReference<@Contextual ParameterDefObject>
typealias FlowGroupReference = ObjectReference<@Contextual AudioFlowGroup>
typealias ScriptObjectReference = ObjectReference<@Contextual ScriptObject>
typealias LiveObjectReference = ObjectReference<@Contextual LiveObject>
typealias LiveTaskReference = ObjectReference<@Contextual LiveTaskObject>
typealias FlowReference = ObjectReference<@Contextual AudioFlow>
typealias VSTPluginReference = ObjectReference<@Contextual VSTPluginFlow>
typealias LiveBufferReference = ObjectReference<@Contextual LiveBufferObject>

fun FlowReference.resolve(context: Context) {
    val allFlows = context.project.flows.allFlows()
    val referenced = allFlows.find { f -> f.name.now == this.getName() }
    if (referenced != null) {
        resolve(allFlows)
    } else {
        setUnresolved()
    }
}