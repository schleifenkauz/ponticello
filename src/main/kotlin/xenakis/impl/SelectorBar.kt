package xenakis.impl

import javafx.beans.value.ObservableValue
import javafx.geometry.Insets
import javafx.scene.Node
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import xenakis.ui.dontDeselectAll
import xenakis.ui.map

abstract class SelectorBar<T>(options: List<T>) : HBox(10.0) {
    protected open fun extractGraphic(option: T): Node? = null
    protected open fun extractText(option: T): String? = null
    protected open fun extractDescription(option: T): String? = null
    protected open fun ToggleButton.extraConfig(option: T) {}

    private val map = mutableMapOf<T, ToggleButton>()
    private val toggleGroup = ToggleGroup()
    private val allButtons = mutableListOf<ToggleButton>()

    init {
        createSegment(options)
        select(options[0])
        toggleGroup.dontDeselectAll()
    }

    val selected: ObservableValue<T> = toggleGroup.selectedToggleProperty().map {
        @Suppress("UNCHECKED_CAST")
        it?.userData as T
    }

    private fun createSegment(options: List<T>) {
        for (option in options) {
            val btn = ToggleButton()
            btn.toggleGroup = toggleGroup
            map[option] = btn
            allButtons.add(btn)
            btn.userData = option
            btn.display(option)
            children.add(btn)
        }
    }

    private fun ToggleButton.display(option: T) {
        text = extractText(option)
        graphic = extractGraphic(option)
        tooltip = this@SelectorBar.extractDescription(option)?.let(::Tooltip)
        if (graphic == null) padding = Insets(11.0)
        extraConfig(option)
    }

    fun reload() {
        for ((option, btn) in map) {
            btn.display(option)
        }
    }

    fun select(option: T) {
        val btn = map.getValue(option)
        btn.isSelected = true
    }

    fun selectIndex(index: Int) {
        if (index in allButtons.indices) allButtons[index].isSelected = true
    }

    fun receiveFocus() {
        val btn = toggleGroup.selectedToggle as ToggleButton
        btn.requestFocus()
    }

    override fun toString(): String = "${this::class.simpleName} [ selected = ${selected.value} ]"
}