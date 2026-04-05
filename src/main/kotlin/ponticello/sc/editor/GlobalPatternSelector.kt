package ponticello.sc.editor

import fxutils.SubWindow
import fxutils.actions.action
import fxutils.actions.makeButton
import fxutils.actions.registerShortcuts
import fxutils.prompt.Prompt
import fxutils.prompt.PromptPlacement
import hextant.context.Context
import hextant.fx.initHextantScene
import javafx.geometry.Pos
import javafx.scene.control.ScrollPane
import javafx.scene.input.DataFormat
import javafx.scene.layout.Region
import javafx.scene.layout.StackPane
import javafx.scene.paint.Color
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import ponticello.model.code.GlobalPatternObject
import ponticello.model.code.GlobalPatternRegistry
import ponticello.ui.dock.AppLayout
import ponticello.ui.misc.CodePane
import ponticello.ui.registry.GlobalPatternRegistryPane

class GlobalPatternSelector : ObjectSelector<GlobalPatternObject>() {
    override fun getOptions(): List<GlobalPatternObject> = context[GlobalPatternRegistry]

    override val canCreateItem: Boolean get() = true

    override fun createNewObject(name: String, promptPlacement: PromptPlacement): GlobalPatternObject? =
        NewGlobalPatternPrompt(name, context).showDialog(promptPlacement)

    override fun dataFormat(): DataFormat = GlobalPatternObject.DATA_FORMAT

    override val canViewSelected: Boolean
        get() = true

    override fun viewObject(obj: GlobalPatternObject) {
        context[AppLayout].get<GlobalPatternRegistryPane>().showContent(obj)
    }

    private class NewGlobalPatternPrompt(
        name: String, private val context: Context
    ) : Prompt<GlobalPatternObject?>() {
        private val pattern = GlobalPatternObject.create(name)
        private val code = pattern.patternCode
        private val confirmButton = confirmAction.withContext(this).makeButton("medium-icon-button")

        init {
            pattern.initialize(context)
            StackPane.setAlignment(confirmButton, Pos.BOTTOM_RIGHT)
        }

        private val scrollPane = ScrollPane(CodePane(code))
        override val content = StackPane(scrollPane, confirmButton)

        override val windowType: SubWindow.Type
            get() = SubWindow.Type.Prompt

        override val title: String
            get() = "Create pattern"

        override fun getDefault(): GlobalPatternObject? = null

        override fun createLayout(): Region = content

        override fun createWindow(content: Region): SubWindow {
            content.setPrefSize(400.0, 100.0)
            val window = super.createWindow(content)
            window.scene.initHextantScene(context)
            content.registerShortcuts(listOf(confirmAction.withContext(this)))
            window.scene.fill = Color.BLACK
            return window
        }

        override fun onReceiveFocus() {
            code.control.receiveFocus()
        }

        companion object {
            private val confirmAction = action<NewGlobalPatternPrompt>("Create pattern") {
                shortcut("Ctrl+K")
                icon(MaterialDesignC.CHECK)
                executes { prompt -> prompt.commit(prompt.pattern) }
            }
        }
    }
}