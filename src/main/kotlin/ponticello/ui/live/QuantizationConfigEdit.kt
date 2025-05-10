package ponticello.ui.live

import fxutils.undo.AbstractEdit
import ponticello.model.live.QuantizationConfig
import ponticello.model.score.ScoreObject

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