package ponticello.ui.misc

import bundles.PublicProperty
import bundles.publicProperty
import fxutils.relocate
import javafx.geometry.Bounds
import javafx.scene.Node
import javafx.scene.web.WebView
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.materialdesign2.MaterialDesignW
import ponticello.sc.Identifier
import ponticello.sc.MessageSend
import ponticello.sc.editor.IdentifierEditor
import ponticello.sc.editor.ScExprEditor
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState
import reaktive.value.now

class HelpBrowser : ToolPane() {
    override val title: String
        get() = "SuperCollider Documentation"

    override val icon: Ikon
        get() = MaterialDesignW.WEB

    override val shortcuts: Array<String> = arrayOf("F1")

    private lateinit var webView: WebView

    override val content: Node
        get() = webView

    override fun defaultState(): ToolPaneState = ToolPaneState.docked(ToolPaneState.Side.RIGHT)

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
        window?.relocate(bounds.minX, bounds.maxY + 10.0)
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
        window?.relocate(bounds.minX, bounds.maxY + 10.0)
    }

    fun searchDocumentation(searchText: String) {
        setShowing(true)
        webView.engine.load("$URL_ROOT/Search.html#$searchText")
    }

    companion object : PublicProperty<HelpBrowser> by publicProperty("help-browser", null) {
        private const val URL_ROOT = "https://doc.sccode.org/"
    }
}