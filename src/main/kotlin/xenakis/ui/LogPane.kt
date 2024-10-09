package xenakis.ui

import hextant.context.Context
import hextant.fx.setPadding
import javafx.application.Platform
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.control.Tooltip
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.text.Text
import org.controlsfx.control.textfield.CustomTextField
import xenakis.model.Logger
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class LogPane(private val context: Context, private val logger: Logger) : VBox(), Logger.View {
    private val boxes = VBox() styleClass "log-records"
    private val scrollPane = ScrollPane(boxes)
    private val searchField = CustomTextField().styleClass("sleek-text-field", "search-field")
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

    private val filter get() = Logger.Filter(level, category, searchField.text)

    init {
        styleClass("tool-pane")
        searchField.left = Icon.Search.getView()
        searchField.promptText = "Search..."
        searchField.textProperty().addListener { _ -> displayFilteredRecords() }
        val heading = Label("Log") styleClass "heading"
        val levelSelector = SimpleSearchableListView(Logger.Level.entries, "Select level")
            .selectorButton(this::level, context) { lvl -> "Level: $lvl" }
        val categorySelector = SimpleSearchableListView(Logger.Category.values(), "Select category")
            .selectorButton(this::category, context) { cat -> "Category: $cat" }
        val buttonClear = button("Clear log") { logger.clear() }
        val header = HBox(
            heading, searchField,
            levelSelector, categorySelector, buttonClear
        ) styleClass "tool-pane-header"
        displayFilteredRecords()
        logger.addView(this)
        scrollPane.isFitToWidth = true
        children.addAll(header, scrollPane)
        setPrefSize(800.0, 800.0)
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
        val icon = record.level.icon.getView()
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
            val viewBtn = Icon.View.button(action = "View details") {
                val text = Text(detailMessage)
                text.fill = Color.WHITE
                val pane = BorderPane(text)
                pane.setMaxSize(1000.0, 1000.0)
                pane.border = solidBorder(Color.GRAY)
                pane.setPadding(5.0)
                val window = SubWindow(pane, "Details", context, SubWindow.Type.Popup, owner = scene.window)
                window.sizeToScene()
                window.show()
            }
            box.children.addAll(infiniteSpace(), viewBtn)
        }
        box.setOnMouseClicked { box.requestFocus() }
        return box
    }
}