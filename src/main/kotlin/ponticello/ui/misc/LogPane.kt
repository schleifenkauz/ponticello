package ponticello.ui.misc

import fxutils.*
import fxutils.actions.button
import fxutils.prompt.SimpleSelectorPrompt
import javafx.application.Platform
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.text.Text
import org.controlsfx.control.textfield.CustomTextField
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.javafx.FontIcon
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignB
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import ponticello.impl.Logger
import ponticello.model.project.PonticelloProject
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class LogPane(private val logger: Logger) : ToolPane(), Logger.View {
    override val type: Type
        get() = LogPane
    private var level = Logger.Level.Info
        set(value) {
            field = value
            displayFilteredRecords()
        }
    private var category: Logger.Category = Logger.Category.All
        set(value) {
            field = value
            displayFilteredRecords()
        }

    private val boxes = VBox() styleClass "log-records"
    private val scrollPane = ScrollPane(boxes)
    private val levelSelector = SimpleSelectorPrompt(Logger.Level.entries, "Select level")
        .selectorButton(this::level, displayText = { lvl -> "Level: $lvl" })
    private val categorySelector = SimpleSelectorPrompt(Logger.Category.values(), "Select category")
        .selectorButton(this::category, displayText = { cat -> "Category: $cat" })
    private val buttonClear = button("Clear log") { logger.clear() }
    private val searchField = CustomTextField().styleClass("sleek-text-field", "search-field")

    override val content: Parent get() = scrollPane
    override val headerContent: Node = HBox(5.0, searchField, levelSelector, categorySelector, buttonClear)
        .centerChildren()

    private val filter get() = Logger.Filter(level, category, searchField.text)

    init {
        styleClass("tool-pane")
    }

    override fun defaultState(): ToolPaneState = ToolPaneState.docked

    override fun afterSetup() {
        searchField.left = FontIcon(Material2MZ.SEARCH)
        searchField.promptText = "Search..."
        searchField.textProperty().addListener { _ -> displayFilteredRecords() }
        displayFilteredRecords()
        logger.addView(this)
        sceneProperty().addListener { _, _, scene ->
            scene?.window?.setOnShowing {
                scrollToEnd()
            }
        }
    }

    private fun scrollToEnd() {
        scrollPane.vvalue = 1.0
    }

    private fun displayFilteredRecords() = Platform.runLater {
        boxes.children.clear()
        for (record in logger.getRecords(filter)) {
            boxes.children.add(createRecordBox(record))
        }
        scrollToEnd()
    }

    override fun logged(record: Logger.Record) {
        if (filter.accepts(record)) {
            Platform.runLater { boxes.children.add(createRecordBox(record)) }
        }
    }

    override fun clearedLog() {
        Platform.runLater { boxes.children.clear() }
    }

    private fun createRecordBox(record: Logger.Record): HBox {
        val icon = FontIcon(record.level.icon)
        var message = record.message
        message = message.replace("\n", "\\n")
        if (message.length > 120) message = message.take(120) + "..."
        val label = Label(message)
        val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(record.timestamp), ZoneId.systemDefault())
        val timeStr = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(time)
        label.tooltip = Tooltip(timeStr)
        val box = HBox(5.0, icon, label).centerChildren() styleClass "log-record"
        box.isFocusTraversable = true
        val detailMessage = record.detailMessage ?: record.message.takeIf { msg -> msg.length > 100 }
        if (detailMessage != null) {
            val viewBtn = MaterialDesignE.EYE.button("View details", "medium-icon-button") {
                val text = Text(detailMessage)
                text.fill = Color.WHITE
                val pane = BorderPane(text)
                pane.setMaxSize(1000.0, 1000.0)
                pane.border = solidBorder(Color.GRAY)
                pane.setPadding(5.0)
                val window = SubWindow(pane, "Details", SubWindow.Type.Popup)
                window.initOwner(scene.window)
                window.sizeToScene()
                window.show()
            }
            box.children.addAll(infiniteSpace(), viewBtn)
        }
        box.setOnMouseClicked { box.requestFocus() }
        return box
    }

    companion object : Type(6, "Notifications") {

        override val icon: Ikon get() = MaterialDesignB.BELL

        override val defaultSide: Side
            get() = Side.RIGHT

        override fun createToolPane(project: PonticelloProject): ToolPane = LogPane(Logger)
    }
}