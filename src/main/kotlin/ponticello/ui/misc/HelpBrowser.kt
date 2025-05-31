package ponticello.ui.misc

import bundles.PublicProperty
import bundles.publicProperty
import fxutils.SubWindow
import javafx.geometry.Bounds
import javafx.scene.web.WebView
import ponticello.sc.Identifier
import ponticello.sc.MessageSend
import ponticello.sc.editor.IdentifierEditor
import ponticello.sc.editor.ScExprEditor
import reaktive.value.now

class HelpBrowser {
    private lateinit var webView: WebView
    private lateinit var window: SubWindow
    private var initialized = false

    private fun initialize() {
        if (initialized) return
        webView = WebView()
        window = SubWindow(webView, "Help Browser", type = SubWindow.Type.Popup)
        webView.setPrefSize(600.0, 800.0)
        webView.engine.load("$URL_ROOT/Help.html")
        window.sizeToScene()
        initialized = true
    }

    fun show() {
        initialize()
        window.showOrBringToFront()
    }

    fun showClassDocumentation(target: ScExprEditor<*>, bounds: Bounds) {
        initialize()
        val name = target.result.now
        if (name !is Identifier || !name.isValidClassName) return
        webView.engine.load("$URL_ROOT/Classes/${name.text}.html")
        show(bounds)
    }

    fun showMethodDocumentation(target: IdentifierEditor, bounds: Bounds) {
        initialize()
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
        initialize()
        window.x = bounds.minX
        window.y = bounds.maxY + 10.0
        show()
    }

    fun searchDocumentation(searchText: String) {
        initialize()
        webView.engine.load("$URL_ROOT/Search.html#$searchText")
        show()
    }

    companion object : PublicProperty<HelpBrowser> by publicProperty("help-browser", null) {
        private const val URL_ROOT = "https://doc.sccode.org/"
    }
}