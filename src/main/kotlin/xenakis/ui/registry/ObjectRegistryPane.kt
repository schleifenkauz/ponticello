package xenakis.ui.registry

import fxutils.*
import javafx.collections.FXCollections
import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.input.DataFormat
import javafx.scene.input.Dragboard
import javafx.scene.input.TransferMode
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import org.controlsfx.control.SearchableComboBox
import org.controlsfx.control.textfield.CustomTextField
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.javafx.FontIcon
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import org.kordamp.ikonli.materialdesign2.MaterialDesignS
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.model.Logger
import xenakis.model.obj.RenamableObject
import xenakis.model.registry.NamedObject
import xenakis.model.registry.ObjectRegistry
import xenakis.sc.Identifier
import xenakis.ui.actions.ActionBar
import xenakis.ui.actions.button
import xenakis.ui.actions.collectActions
import xenakis.ui.actions.registerShortcuts
import xenakis.ui.controls.NameControl
import xenakis.ui.controls.NamePrompt
import xenakis.ui.impl.label

abstract class ObjectRegistryPane<O : NamedObject>(
    private val registry: ObjectRegistry<O>
) : VBox(), ObjectRegistry.Listener<O> {
    protected val layout = VBox()
    private val boxes = mutableListOf<ObjectBox<O>>()
    protected val searchText = CustomTextField().styleClass("sleek-text-field", "search-field")
    private val header = createHeader()

    init {
        maxHeight = 1000.0
        styleClass.add("tool-pane")
        val scrollPane = ScrollPane(layout)
        scrollPane.isFitToWidth = true
        children.addAll(header, scrollPane)
        SplitPane.setResizableWithParent(this, false)
        HBox.setHgrow(searchText, Priority.ALWAYS)
        searchText.promptText = "Search..."
        searchText.left = FontIcon(Material2MZ.SEARCH)
        searchText.textProperty().addListener { _, _, _ -> layoutBoxes() }
        registerShortcuts(actions.withContext(this))
        layoutBoxes()
    }

    private fun createHeader(): HBox {
        val type = registry.objectType
        val label = Label(plural(type)).styleClass("heading")
        val space = infiniteSpace()
        val actionBar = ActionBar(actions.withContext(this), border = false)
        val moveBtn = actionBar.getButton(actions.getAction("Move window"))
        moveBtn.setupWindowDragButton { scene.window }
        return HBox(label, searchText, space, actionBar).styleClass("tool-pane-header")
    }

    override fun requestFocus() {
        searchText.requestFocus()
    }

    protected fun <T : Any> showCreateNewDialog(options: List<T>, default: T, createObject: (T, String) -> O?) {
        val typeSelector = SearchableComboBox(FXCollections.observableList(options))
        typeSelector.value = default
        val nameInput = TextField() styleClass "prompt-text-field"
        nameInput.promptText = "${registry.objectType} name"
        val ok = Material2AL.CHECK.button(action = "Confirm")
        val layout = HBox(typeSelector, nameInput).centerChildren() styleClass "prompt"
        val window = SubWindow(
            layout, "Create new ${registry.objectType}",
            type = SubWindow.Type.Popup
        )
        window.initOwner(scene.window)
        window.setOnShown { nameInput.requestFocus() }
        fun commit() {
            val type = typeSelector.value ?: return
            val name = nameInput.text
            if (!Identifier.isValid(name) || registry.has(name)) return
            window.hide()
            val obj = createObject(type, name) ?: return
            registry.add(obj)
        }
        ok.setOnAction { commit() }
        layout.registerShortcuts {
            on("ENTER") { commit() }
        }
        window.sizeToScene()
        window.show()
    }

    private fun layoutBoxes() {
        layout.children.clear()
        for (box in boxes) {
            if (matchesSearch(box.obj)) layout.children.add(box)
        }
        if (scene != null) scene.window.sizeToScene()
    }

    protected abstract fun sync()

    protected open fun addObject() {
        val name = NamePrompt(registry, "Name for new ${registry.objectType}", initialName = searchText.text)
            .showDialog(anchorNode = this) ?: return
        addObject(name)
    }

    protected abstract fun addObject(name: String): O?

    protected open fun canDelete(obj: O): Boolean = true

    protected open fun ObjectBox<O>.configureObjectBox() {}

    private fun matchesSearch(obj: O) = obj.name.now.contains(searchText.text, ignoreCase = true)

    override fun added(obj: O, idx: Int) {
        val box = ObjectBox(this, obj)
        box.configureObjectBox()
        boxes.add(box)
        layoutBoxes()
    }

    override fun removed(obj: O, idx: Int) {
        val box = boxes.removeAt(idx)
        layout.children.remove(box)
        if (scene != null) scene.window.sizeToScene()
    }

    fun box(idx: Int): ObjectBox<O> = boxes[idx]

    class ObjectBox<O : NamedObject>(private val pane: ObjectRegistryPane<O>, val obj: O) : HBox() {
        val actions = HBox().centerChildren()

        private val extraControls = HBox(5.0).centerChildren()

        init {
            styleClass("object-box")
            val nameDisplay =
                if (obj is RenamableObject) NameControl(obj)
                else HBox(label(obj.name).styleClass("name-field")).styleClass("name")
            setHgrow(nameDisplay, Priority.ALWAYS)
            maxWidth = Double.MAX_VALUE
            children.addAll(nameDisplay, extraControls, actions)
            addAction(Material2AL.DELETE, "Remove object") { pane.registry.remove(obj) }.isDisable =
                !pane.canDelete(obj)
        }

        fun addAction(icon: Ikon, description: String, action: () -> Unit): Button {
            val button = icon.button(action = description) { action() }
            button.styleClass("object-action-button")
            actions.children.add(0, button)
            return button
        }

        fun addGrabber(dataFormat: DataFormat, transferMode: TransferMode, extraConfig: Dragboard.() -> Unit = {}) {
            val btn = addAction(MaterialDesignC.CURSOR_POINTER, "Grab object") { }
            btn.setOnDragDetected { ev ->
                val db = startDragAndDrop(transferMode)
                db.setContent(mapOf(dataFormat to obj.name.now))
                db.extraConfig()
                ev.consume()
            }
        }

        fun addExtraControl(vararg node: Node) {
            extraControls.children.addAll(*node)
        }
    }

    companion object {
        private val actions = collectActions<ObjectRegistryPane<*>> {
            addAction("Create object") {
                description { p -> reactiveValue("Create new ${p.registry.objectType}") }
                shortcut("Ctrl+PLUS")
                icon(MaterialDesignP.PLUS)
                executes { p -> p.addObject() }
            }
            addAction("Sync registry") {
                description { p -> reactiveValue("Sync ${plural(p.registry.objectType)}") }
                shortcut("Ctrl+Shift+S")
                icon(MaterialDesignS.SYNC)
                executes { p ->
                    p.sync()
                    Logger.confirm(
                        "Synchronized ${plural(p.registry.objectType)} with server",
                        Logger.Category.Registries
                    )
                }
            }
            addAction("Move window") {
                icon(MaterialDesignC.CURSOR_MOVE)
            }
            addAction("Close window") {
                shortcut("Ctrl+W")
                icon(MaterialDesignC.CLOSE)
                executes { p -> p.scene.window.hide() }
            }
        }
    }
}