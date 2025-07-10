package ponticello.ui.misc

import fxutils.*
import fxutils.actions.ActionBar
import fxutils.actions.collectActions
import fxutils.prompt.SimpleTextPrompt
import javafx.beans.binding.Bindings
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.input.KeyEvent
import javafx.scene.web.WebView
import javafx.stage.Popup
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignA
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import org.kordamp.ikonli.materialdesign2.MaterialDesignW
import ponticello.model.project.PonticelloProject
import ponticello.sc.Identifier
import ponticello.sc.MessageSend
import ponticello.sc.editor.IdentifierEditor
import ponticello.sc.editor.ScExprEditor
import ponticello.ui.dock.BrowserPaneState
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.impl.showDialog
import reaktive.value.fx.asReactiveValue
import reaktive.value.now

class HelpBrowser : ToolPane() {
    override val type: Type
        get() = HelpBrowser

    private lateinit var webView: WebView

    override val content: Parent
        get() = webView

    override fun defaultState(): ToolPaneState = BrowserPaneState.default()

    override fun doSetup() {
        webView = WebView()
        webView.setPrefSize(600.0, 800.0)
        val state = initialState
        if (state is BrowserPaneState) {
            webView.engine.load(state.url)
        } else {
            webView.engine.load(DEFAULT_URL)
        }
    }

    override fun saveState(dest: ToolPaneState) {
        super.saveState(dest)
        if (dest is BrowserPaneState) {
            dest.url = webView.engine.location ?: DEFAULT_URL
        }
    }

    override fun afterSetup() {
        val actionBar = ActionBar(browserActions.withContext(this), "medium-icon-button")
        header.children.addAfter(heading, actionBar)
    }

    fun showClassDocumentation(target: ScExprEditor<*>, anchor: Node) {
        setShowing(true, ownerWindow = anchor.scene.window)
        val name = target.result.now
        if (name !is Identifier || !name.isValidClassName) return
        webView.engine.load("$URL_ROOT/Classes/${name.text}.html")
        if (window is Popup) {
            val bounds = anchor.localToScreen(anchor.boundsInLocal)
            window?.relocate(bounds.minX, bounds.maxY + 10.0)
        }
    }

    fun showMethodDocumentation(target: IdentifierEditor, anchor: Node) {
        setShowing(true, ownerWindow = anchor.scene.window)
        val name = target.result.now
        if (!name.isValid) return
        val (receiver, _, _) = target.parent?.result?.now as MessageSend
        if (receiver is Identifier && receiver.isValidClassName) {
            webView.engine.load("$URL_ROOT/Classes/${receiver.text}.html#-${name.text}")
        } else {
            webView.engine.load("$URL_ROOT/Overviews/Methods.html#${name.text}")
        }
        if (window is Popup) {
            val bounds = anchor.localToScreen(anchor.boundsInLocal)
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

    companion object : Type(uid = 12, "SuperCollider Documentation") {
        override val icon: Ikon
            get() = MaterialDesignW.WEB

        override val shortcuts: Array<String> = arrayOf("F1")

        override val defaultSide: Side
            get() = Side.RIGHT

        override fun createToolPane(project: PonticelloProject): ToolPane = HelpBrowser()

        private const val URL_ROOT = "https://doc.sccode.org/"

        const val DEFAULT_URL = "$URL_ROOT/Help.html"

        private val browserActions = collectActions<HelpBrowser> {
            addAction("Back") {
                icon(MaterialDesignA.ARROW_LEFT)
                shortcut("Alt+LEFT")
                enableWhen { browser ->
                    browser.webView.engine.history.currentIndexProperty().greaterThan(0).asReactiveValue()
                }
                executes { browser ->
                    val history = browser.webView.engine.history
                    if (history.currentIndex > 0) {
                        history.go(-1)
                    }
                }
            }
            addAction("Forward") {
                icon(MaterialDesignA.ARROW_RIGHT)
                shortcut("Alt+RIGHT")
                enableWhen { browser ->
                    val history = browser.webView.engine.history
                    val nEntries = Bindings.size(history.entries)
                    history.currentIndexProperty().lessThan(nEntries.subtract(1)).asReactiveValue()
                }
                executes { browser ->
                    val history = browser.webView.engine.history
                    if (history.currentIndex < history.entries.lastIndex) {
                        history.go(+1)
                    }
                }
            }
            addAction("Reload") {
                icon(MaterialDesignR.RELOAD)
                executes { browser ->
                    browser.webView.engine.reload()
                }
            }
        }
    }
}