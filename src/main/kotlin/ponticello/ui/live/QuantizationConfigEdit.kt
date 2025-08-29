package ponticello.ui.live

import fxutils.undo.AbstractEdit
import ponticello.model.live.QuantizationConfig
import ponticello.model.player.PlayListItem

class QuantizationConfigEdit(
    private val item: PlayListItem,
    private val before: QuantizationConfig, private val after: QuantizationConfig,
) : AbstractEdit() {
    override val actionDescription: String
        get() = "Update quantization configuration"

    override fun doRedo() {
        item.quantization.update(after)
    }

    override fun doUndo() {
        item.quantization.update(before)
    }
}