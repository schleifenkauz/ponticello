package ponticello.ui.live

import fxutils.actions.collectActions
import fxutils.actions.contextMenu
import fxutils.button
import fxutils.centerChildren
import fxutils.setPseudoClassState
import fxutils.styleClass
import javafx.geometry.Side.BOTTOM
import javafx.scene.control.Button
import javafx.scene.input.MouseButton
import javafx.scene.layout.HBox
import org.kordamp.ikonli.materialdesign2.MaterialDesignD
import ponticello.model.midi.MidiGridInstrument

class GridBanksBar(private val grid: MidiGridInstrument) : MidiGridInstrument.View, HBox(5.0) {
    private val bankSelectors = mutableListOf<Button>()
    private val addBankButton = button("+", style = "grid-bank-selector")

    init {
        grid.addView(this)
        children.addAll(addBankButton)
        centerChildren()
        addBankButton.setOnAction { grid.addBank() }
    }

    override fun addedBank(bankIndex: Int) {
        val btn = Button().styleClass("grid-bank-selector")
        bankSelectors.add(bankIndex, btn)
        children.add(bankIndex, btn)
        btn.setOnMouseClicked { ev ->
            val bankIdx = bankSelectors.indexOf(btn)
            when (ev.button) {
                MouseButton.PRIMARY -> grid.selectBank(bankIdx)
                MouseButton.SECONDARY -> {
                    contextMenu(bankActions.withContext(Pair(grid, bankIdx))).show(btn, BOTTOM, 0.0, 0.0)
                }

                else -> {}
            }
            ev.consume()
        }
        updateBankButtonLabels(bankIndex)
    }

    override fun removedBank(bankIndex: Int) {
        bankSelectors.removeAt(bankIndex)
        children.removeAt(bankIndex)
        updateBankButtonLabels(bankIndex)
    }

    override fun selectedBank(bank: Int) {
        for ((idx, selector) in bankSelectors.withIndex()) {
            selector.setPseudoClassState("selected", idx == bank)
        }
    }

    private fun updateBankButtonLabels(fromIndex: Int) {
        for ((idx, selector) in bankSelectors.withIndex().drop(fromIndex)) {
            selector.text = (idx + 1).toString()
        }
    }

    companion object {
        private val bankActions = collectActions<Pair<MidiGridInstrument, Int>> {
            addAction("Remove bank") {
                icon(MaterialDesignD.DELETE)
                executes { (grid, bankIndex) -> grid.removeBank(bankIndex) }
            }
        }
    }
}