package xenakis.ui

import hextant.context.Context
import hextant.fx.PseudoClasses.SELECTED
import hextant.fx.shortcut
import javafx.geometry.Point2D
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.input.KeyEvent
import javafx.scene.layout.VBox
import org.controlsfx.control.textfield.CustomTextField
import reaktive.event.event

abstract class SearchableListView<E> : VBox() {
    private val searchText = CustomTextField().styleClass("sleek-text-field")
    private val optionsBox = VBox().styleClass("options-box")

    private val optionBoxes = mutableMapOf<E, Node>()
    private var filteredOptions: List<E> = emptyList()
    var selectedOption: E? = null
        private set

    private val confirmOption = event<E>()

    val confirmedOption get() = confirmOption.stream

    val removedOptions = mutableSetOf<E>()

    protected abstract fun options(): List<E>

    init {
        styleClass("searchable-list")
        searchText.left = Label("", Icon.Search.getView(Icon.DEFAULT_RADIUS))
        setMaxSize(300.0, 500.0)
        children.addAll(searchText, optionsBox)
        registerShortcuts()
        searchText.textProperty().addListener { _, _, txt -> updatedText(txt) }
    }

    private fun initializeOptions() {
        prepareOptionBoxes()
        updatedText(searchText.text)
    }

    protected open fun makeOption(text: String): E? = null

    protected abstract fun createCell(option: E): Node

    protected abstract fun extractText(option: E): String

    override fun requestFocus() {
        searchText.requestFocus()
        searchText.selectAll()
    }

    private fun updatedText(txt: String) {
        filteredOptions = options().filter { option -> extractText(option).contains(txt, ignoreCase = true) }
        select(filteredOptions.firstOrNull { option -> option !in removedOptions })
        layoutOptionBoxes()
        scene.window.sizeToScene()
    }

    private fun prepareOptionBoxes() {
        optionBoxes.clear()
        for (option in options()) {
            val box = createCell(option).styleClass("option-cell")
            box.setOnMouseClicked {
                confirm(option)
            }
            optionBoxes[option] = box
        }
    }

    private fun layoutOptionBoxes() {
        optionsBox.children.clear()
        for (option in filteredOptions) {
            if (option in removedOptions) continue
            val box = optionBoxes.getValue(option)
            optionsBox.children.add(box)
        }
    }

    private fun registerShortcuts() {
        addEventFilter(KeyEvent.KEY_PRESSED) { ev ->
            when {
                "Shift?+TAB".shortcut.matches(ev) -> {
                    val deltaIdx = if (ev.isShiftDown) -1 else +1
                    val selectedIndex = filteredOptions.indexOf(selectedOption)
                    if (selectedIndex + deltaIdx in filteredOptions.indices) {
                        select(filteredOptions[selectedIndex + deltaIdx])
                    }
                    ev.consume()
                }

                "Enter".shortcut.matches(ev) -> {
                    if (selectedOption != null) confirm(selectedOption!!)
                    else confirmText(searchText.text)
                    ev.consume()
                }

                "Ctrl+Enter".shortcut.matches(ev) -> {
                    confirmText(searchText.text)
                    ev.consume()
                }
            }
        }
    }

    fun select(option: E?) {
        optionBoxes[selectedOption]?.pseudoClassStateChanged(SELECTED, false)
        selectedOption = option
        optionBoxes[selectedOption]?.pseudoClassStateChanged(SELECTED, true)
    }

    private fun confirm(option: E) {
        hide()
        confirmOption.fire(option)
    }

    private fun confirmText(text: String) {
        val option = makeOption(text) ?: return
        confirm(option)
    }

    fun enterText(text: String) {
        searchText.text = text
    }

    fun showPopup(
        context: Context, title: String, anchor: Point2D? = null,
        initialOption: E? = null, onConfirm: (E) -> Unit
    ) {
        initializeOptions()
        if (initialOption in filteredOptions) select(initialOption)
        val window = SubWindow(this, title, context, type = SubWindow.Type.Popup)
        if (anchor != null) {
            window.x = anchor.x
            window.y = anchor.y
        } else window.centerOnScreen()
        val observer = confirmedOption.observe { _, option -> onConfirm(option) }
        window.showAndWait()
    }

    fun showPopup(context: Context, title: String, anchorNode: Node, initialOption: E? = null, onConfirm: (E) -> Unit) {
        val anchor = anchorNode.localToScreen(0.0, 0.0)
        showPopup(context, title, anchor, initialOption, onConfirm)
    }

    protected fun hide() {
        scene.window.hide()
    }
}