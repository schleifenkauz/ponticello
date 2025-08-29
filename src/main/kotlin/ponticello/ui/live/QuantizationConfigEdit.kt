package ponticello.ui.live

import fxutils.undo.AbstractEdit
import ponticello.model.live.QuantizationConfig

class QuantizationConfigEdit(
    private val config: QuantizationConfig,
    private val before: QuantizationConfig, private val after: QuantizationConfig,
) : AbstractEdit() {
    override val actionDescription: String
        get() = "Update quantization configuration"

    override fun doRedo() {
        config.update(after)
    }

    override fun doUndo() {
        config.update(before)
    }
}