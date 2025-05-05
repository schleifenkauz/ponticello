package xenakis.model.obj

import reaktive.value.now
import xenakis.impl.Decimal
import xenakis.impl.Logger
import xenakis.impl.zero
import xenakis.model.player.ScorePlayer

class MeterClock {
    private val activePlayers = mutableSetOf<ActivePlayer>()

    private fun removeInactivePlayers() {
        activePlayers.removeIf { (p) -> !p.isPlaying.now }
    }

    fun scheduleStart(player: ScorePlayer, quant: Decimal, offset: Decimal): Decimal {
        removeInactivePlayers()
        activePlayers.removeIf { (p) -> p == player }
        if (activePlayers.isEmpty()) {
            assert(activePlayers.isEmpty())
            activePlayers.add(ActivePlayer(player, offset))
            return zero
        } else {
            val meterTime = getMeterTime()
            var time = meterTime //TODO find a good name...
            while (time < zero) time += quant
            while (time >= quant) time -= quant
            val delay = quant - time
            activePlayers.add(ActivePlayer(player, meterTime + delay))
            if (delay < zero) {
                Logger.warn("Negative delay detected: $delay", Logger.Category.Playback)
            }
            return delay.coerceAtLeast(zero)
        }
    }

    private fun getMeterTime(): Decimal {
        val (activePlayer, start) = activePlayers.first()
        return start + activePlayer.elapsedTime
    }

    fun attach(player: ScorePlayer, offset: Decimal) {
        removeInactivePlayers()
        if (activePlayers.isEmpty()) {
            activePlayers.add(ActivePlayer(player, offset - player.elapsedTime))
        } else if (activePlayers.none { (p) -> p == player }) {
            val meterTime = getMeterTime()
            activePlayers.add(ActivePlayer(player, offset - player.elapsedTime + meterTime))
        }
    }

    fun detach(player: ScorePlayer) {
        activePlayers.removeIf { (p) -> p == player }
    }

    private data class ActivePlayer(val player: ScorePlayer, val playerOffset: Decimal)
}