package xenakis.ui

import hextant.context.Context
import hextant.serial.EditorRoot
import xenakis.model.SuperColliderObject
import xenakis.sc.editor.CodeBlockEditor

class SetupCodePane(
    private val code: EditorRoot<CodeBlockEditor>,
    private val liveCycleType: SuperColliderObject.LiveCycleType,
    context: Context
) {
    val window = SubWindow(code.control, "Setup code: $liveCycleType", context)

}