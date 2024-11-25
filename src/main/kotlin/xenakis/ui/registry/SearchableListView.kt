package xenakis.ui.registry

import hextant.context.Context
import hextant.fx.PseudoClasses.SELECTED
import hextant.fx.runFXWithTimeout
import hextant.fx.shortcut
import javafx.geometry.Point2D
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseButton
import javafx.scene.layout.VBox
import javafx.stage.Window
import org.controlsfx.control.textfield.CustomTextField
import reaktive.event.event
import reaktive.value.ReactiveVariable
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue
import xenakis.ui.Icon
import xenakis.ui.impl.SubWindow
import xenakis.ui.impl.button
import xenakis.ui.impl.styleClass
import kotlin.reflect.KMutableProperty0

abstract class SearchableListView<E>(private val title: String) : VBox() {
    private val searchText = CustomTextField().styleClass("sleek-text-field", "search-field")
    private val optionsBox = VBox().styleClass("options-box")

    private val optionBoxes = mutableMapOf<E, Node>()
    private var filteredOptions: List<E> = emptyList()
    var selectedOption: E? = null
        private set

    private val confirmOption = event<E>()

    val confirmedOption get() = confirmOption.stream

    val removedOptions = mutableSetOf<E>()

    private var window: SubWindow? = null

    protected abstract fun options(): List<E>

    init {
        styleClass("searchable-list")
        searchText.promptText = "$title..."
        searchText.left = Label("", Icon.Search.getView(Icon.DEFAULT_RADIUS))
        optionsBox.setMaxSize(300.0, 500.0)
        children.addAll(searchText, optionsBox)
        registerShortcuts()
        searchText.textProperty().addListener { _, _, txt -> updatedText(txt) }
    }

    protected fun getBox(option: E) = optionBoxes[option]

    private fun initializeOptions() {
        prepareOptionBoxes()
        updatedText(searchText.text)
    }

    protected open fun makeOption(text: String): E? = null

    protected abstract fun createCell(option: E): Node

    protected abstract fun extractText(option: E): String

    protected open fun displayText(option: E): String = extractText(option)

    override fun requestFocus() {
        searchText.requestFocus()
        searchText.selectAll()
    }

    private fun updatedText(txt: String) {
        filteredOptions = options().filter { option -> extractText(option).contains(txt, ignoreCase = true) }
        select(filteredOptions.firstOrNull { option -> option !in removedOptions })
        layoutOptionBoxes()
        if (scene != null) scene.window.sizeToScene()
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
        runFXWithTimeout {
            confirmOption.fire(option)
        }
    }

    private fun confirmText(text: String) {
        val option = makeOption(text) ?: return
        confirm(option)
    }

    fun enterText(text: String) {
        searchText.text = text
    }

    fun showPopup(
        context: Context, anchor: Point2D? = null, owner: Window? = null, initialOption: E? = null,
        onConfirm: (E) -> Unit
    ) {
        initializeOptions()
        if (initialOption in filteredOptions) select(initialOption)
        if (window == null) window = SubWindow(this, title, context, type = SubWindow.Type.Popup, owner)
        if (anchor != null) {
            window!!.x = anchor.x
            window!!.y = anchor.y
        } else window!!.centerOnScreen()
        userData = confirmedOption.observe { _, option -> onConfirm(option) }
        window!!.showAndWait()
    }

    fun showPopup(context: Context, anchorNode: Node, initialOption: E? = null, onConfirm: (E) -> Unit) {
        val anchor = anchorNode.localToScreen(0.0, 0.0)
        showPopup(context, anchor, anchorNode.scene?.window, initialOption, onConfirm)
    }

    fun selectorButton(
        property: KMutableProperty0<E>, context: Context, default: E = property.get(),
        displayText: (E) -> String = this::displayText
    ): Button = button(displayText(property.get())).apply {
        showPopupOnClick(context, default, property::get) { value ->
            property.set(value)
            text = displayText(value)
        }
    }

    fun selectorButton(
        property: ReactiveVariable<E>, context: Context, default: E = property.get(),
        displayText: (E) -> String = this::displayText
    ): Button = button().apply {
        textProperty().bind(property.map(displayText).asObservableValue())
        showPopupOnClick(context, default, property::get) { value -> property.set(value) }
    }

    private fun Button.showPopupOnClick(context: Context, default: E, get: () -> E, onSelect: (E) -> Unit) {
        setOnMouseClicked { ev ->
            when (ev.button) {
                MouseButton.PRIMARY -> {
                    showPopup(context, anchorNode = this, initialOption = get.invoke()) { option ->
                        onSelect(option)
                    }
                }

                MouseButton.SECONDARY -> {
                    onSelect(default)
                }

                else -> {}
            }
        }
    }

    protected fun hide() {
        scene.window.hide()
    }
}