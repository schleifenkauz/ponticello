package xenakis.ui.misc

import bundles.PublicProperty
import bundles.publicProperty
import fxutils.SubWindow
import javafx.geometry.Bounds
import javafx.scene.web.WebView
import reaktive.value.now
import xenakis.sc.Identifier
import xenakis.sc.MessageSend
import xenakis.sc.editor.IdentifierEditor
import xenakis.sc.editor.ScExprEditor

class HelpBrowser {
    private val webView: WebView = WebView()

    private val window = SubWindow(webView, "Help Browser", type = SubWindow.Type.Popup)

    init {
        webView.setPrefSize(600.0, 800.0)
        webView.engine.load("$URL_ROOT/Help.html")
        window.sizeToScene()
    }

    fun show() {
        window.showOrBringToFront()
    }

    fun showClassDocumentation(target: ScExprEditor<*>, bounds: Bounds) {
        val name = target.result.now
        if (name !is Identifier || !name.isValidClassName) return
        webView.engine.load("$URL_ROOT/Classes/${name.text}.html")
        show(bounds)
    }

    fun showMethodDocumentation(target: IdentifierEditor, bounds: Bounds) {
        val name = target.result.now
        if (!name.isValid) return
        val (receiver, _, _) = target.parent?.result?.now as MessageSend
        if (receiver is Identifier && receiver.isValidClassName) {
            webView.engine.load("$URL_ROOT/Classes/${receiver.text}.html#-${name.text}")
        } else {
            webView.engine.load("$URL_ROOT/Overviews/Methods.html#${name.text}")
        }
        show(bounds)
    }

    private fun show(bounds: Bounds) {
        window.x = bounds.minX
        window.y = bounds.maxY + 10.0
        show()
    }

    fun searchDocumentation(searchText: String) {
        webView.engine.load("$URL_ROOT/Search.html#$searchText")
        show()
    }

    companion object : PublicProperty<HelpBrowser> by publicProperty("help-browser", null) {
        private const val URL_ROOT = "https://doc.sccode.org/"
    }
}