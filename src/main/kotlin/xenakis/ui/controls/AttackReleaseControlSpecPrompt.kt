package xenakis.ui.controls

import fxutils.prompt.TextPrompt
import reaktive.value.now
import xenakis.impl.parseDecimal
import xenakis.impl.zero
import xenakis.sc.AttackReleaseControlSpec

class AttackReleaseControlSpecPrompt(
    private val initialSpec: AttackReleaseControlSpec, title: String,
) : TextPrompt<AttackReleaseControlSpec>(title, initialSpec.maxDuration.now?.toCanonicalString() ?: "") {
    override fun convert(text: String): AttackReleaseControlSpec? {
        val totalDuration = text.parseDecimal() ?: return null
        if (totalDuration < zero) return null
        initialSpec.maxDuration.set(totalDuration)
        return initialSpec
    }
}