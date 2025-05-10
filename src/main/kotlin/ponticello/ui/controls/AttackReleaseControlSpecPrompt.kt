package ponticello.ui.controls

import fxutils.prompt.TextPrompt
import ponticello.impl.parseDecimal
import ponticello.impl.zero
import ponticello.sc.AttackReleaseControlSpec
import reaktive.value.now

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