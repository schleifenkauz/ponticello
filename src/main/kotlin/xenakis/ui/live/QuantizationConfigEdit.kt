package xenakis.ui.live

import fxutils.undo.AbstractEdit
import xenakis.model.live.QuantizationConfig
import xenakis.model.score.ScoreObject

class QuantizationConfigEdit(
    private val obj: ScoreObject,
    private val before: QuantizationConfig, private val after: QuantizationConfig,
) : AbstractEdit() {
    override val actionDescription: String
        get() = "Update quantization configuration"

    override fun doRedo() {
        obj.quantizationConfig.update(after)
    }

    override fun doUndo() {
        obj.quantizationConfig.update(before)
    }
}