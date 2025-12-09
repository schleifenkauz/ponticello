package ponticello.model.player

import ponticello.impl.Decimal
import ponticello.impl.zero
import ponticello.model.instr.ParameterDefObject
import ponticello.model.obj.project
import ponticello.model.project.PLAYBACK_SETTINGS
import ponticello.model.project.get
import ponticello.model.score.ObjectPosition
import ponticello.model.score.ScoreObjectInstance
import ponticello.model.score.controls.ParameterControl
import reaktive.value.now

data class ObjectPlaybackInfo(
    val pos: ObjectPosition,
    val player: ScorePlayer,
    val serverLatency: Decimal = player.context.project[PLAYBACK_SETTINGS].serverLatency.now,
    val cutoff: Decimal = zero,
    val instance: ScoreObjectInstance? = null,
    val extraArguments: Map<ParameterDefObject, ParameterControl> = emptyMap()
)