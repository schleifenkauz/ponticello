package ponticello.ui.record

import fxutils.bindPseudoClassState
import fxutils.removeColumn
import fxutils.removeRow
import fxutils.styleClass
import javafx.scene.control.Button
import javafx.scene.layout.GridPane
import reaktive.Observer
import reaktive.and
import reaktive.value.ReactiveInt
import reaktive.value.ReactiveVariable
import reaktive.value.binding.equalTo
import reaktive.value.now
import reaktive.value.reactiveVariable

class ChannelMappingGrid(
    private val sourceChannels: ReactiveInt,
    private val outputChannels: ReactiveInt
) : GridPane() {
    private val mapping = MutableList(outputChannels.now) { idx -> reactiveVariable(idx) }

    private val observer: Observer

    init {
        hgap = 5.0
        vgap = 5.0
        observer = sourceChannels.observe { _, old, new ->
            if (new > old) {
                addCells(old, new)
            } else if (new < old) {
                for (i in new until old) {
                    removeColumn(i)
                }
            }
        } and outputChannels.observe { _, prevChannels, channels ->
            if (channels > prevChannels) {
                for (ch in prevChannels until channels) {
                    if (ch !in mapping.indices) {
                        val inputIndex = reactiveVariable(ch)
                        mapping.add(inputIndex)
                    }
                    for (i in 0 until sourceChannels.now) {
                        add(createCell(i, mapping[ch]), /*column*/i,/*row*/ ch)
                    }
                }
            } else if (channels < prevChannels) {
                for (ch in channels until prevChannels) {
                    removeRow(ch)
                }
            }
        }
    }

    private fun addCells(sourceChannelsBefore: Int, sourceChannels: Int) {
        for (ch in 0 until outputChannels.now) {
            for (i in sourceChannelsBefore until sourceChannels) {
                add(createCell(i, mapping[ch]), /*column*/i, /*row*/ ch)
            }
        }
    }

    fun initialize() {
        addCells(0, sourceChannels.now)
    }

    fun getMapping() = mapping.take(outputChannels.now).map { inputIndex -> inputIndex.now }

    private fun createCell(targetIndex: Int, mappingVar: ReactiveVariable<Int>): Button {
        val btn = Button(targetIndex.toString()) styleClass "channel-grid-cell"
        btn.setOnAction { mappingVar.set(targetIndex) }
        btn.userData = btn.bindPseudoClassState("selected", mappingVar.equalTo(targetIndex))
        return btn
    }

    fun initialize(channelMapping: List<Int>) {
        mapping.zip(channelMapping).forEach { (variable, ch) -> variable.set(ch) }
        initialize()
    }
}