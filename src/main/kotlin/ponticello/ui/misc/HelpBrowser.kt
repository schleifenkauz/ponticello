package ponticello.ui.misc

import bundles.PublicProperty
import bundles.publicProperty
import fxutils.Ctrl
import fxutils.modifiers
import fxutils.noModifiers
import fxutils.prompt.SimpleTextPrompt
import fxutils.relocate
import javafx.geometry.Bounds
import javafx.scene.Parent
import javafx.scene.input.KeyEvent
import javafx.scene.web.WebView
import javafx.stage.Popup
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignW
import ponticello.model.project.PonticelloProject
import ponticello.sc.Identifier
import ponticello.sc.MessageSend
import ponticello.sc.editor.IdentifierEditor
import ponticello.sc.editor.ScExprEditor
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.impl.showDialog
import reaktive.value.now

class HelpBrowser : ToolPane() {
    override val type: Type
        get() = HelpBrowser

    private lateinit var webView: WebView

    override val content: Parent
        get() = webView

    override fun defaultState(): ToolPaneState = ToolPaneState.docked

    override fun doSetup() {
        webView = WebView()
        webView.setPrefSize(600.0, 800.0)
        webView.engine.load("$URL_ROOT/Help.html")
    }

    fun showClassDocumentation(target: ScExprEditor<*>, bounds: Bounds) {
        setShowing(true)
        val name = target.result.now
        if (name !is Identifier || !name.isValidClassName) return
        webView.engine.load("$URL_ROOT/Classes/${name.text}.html")
        if (window is Popup) {
            window?.relocate(bounds.minX, bounds.maxY + 10.0)
        }
    }

    fun showMethodDocumentation(target: IdentifierEditor, bounds: Bounds) {
        setShowing(true)
        val name = target.result.now
        if (!name.isValid) return
        val (receiver, _, _) = target.parent?.result?.now as MessageSend
        if (receiver is Identifier && receiver.isValidClassName) {
            webView.engine.load("$URL_ROOT/Classes/${receiver.text}.html#-${name.text}")
        } else {
            webView.engine.load("$URL_ROOT/Overviews/Methods.html#${name.text}")
        }
        if (window is Popup) {
            window?.relocate(bounds.minX, bounds.maxY + 10.0)
        }
    }

    fun searchDocumentation(searchText: String) {
        setShowing(true)
        webView.engine.load("$URL_ROOT/Search.html#$searchText")
    }

    override fun handleShortcut(ev: KeyEvent) {
        when (ev.modifiers) {
            noModifiers -> super.handleShortcut(ev)
            setOf(Ctrl) -> {
                val searchText = SimpleTextPrompt("Look up documentation", "")
                    .showDialog(context) ?: return
                searchDocumentation(searchText)
            }
        }
    }

    companion object : PublicProperty<HelpBrowser> by publicProperty("help-browser", null),
        Type(12, "SuperCollider Documentation") {
        override val icon: Ikon
            get() = MaterialDesignW.WEB

        override val shortcuts: Array<String> = arrayOf("F1")

        override val defaultSide: Side
            get() = Side.RIGHT

        override fun createToolPane(project: PonticelloProject): ToolPane = HelpBrowser()

        private const val URL_ROOT = "https://doc.sccode.org/"
    }
}