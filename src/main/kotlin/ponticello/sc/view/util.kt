package ponticello.sc.view

import bundles.set
import hextant.core.editor.ListEditor
import hextant.core.view.CompoundEditorControl.Layout
import hextant.core.view.EditorControl
import hextant.core.view.ListEditorControl
import hextant.core.view.ListEditorControl.Companion.ADD_WITH_COMMA
import hextant.core.view.ListEditorControl.Companion.CELL_FACTORY
import hextant.core.view.ListEditorControl.Companion.ORIENTATION
import hextant.core.view.ListEditorControl.Orientation

fun Layout.viewHorizontal(list: ListEditor<*, *>): EditorControl<*> = view(list, cached = true) {
    set(ORIENTATION, Orientation.Horizontal)
    set(CELL_FACTORY) { ListEditorControl.SeparatorCell(", ") }
    set(ADD_WITH_COMMA, true)
}

fun Layout.viewVertical(list: ListEditor<*, *>): EditorControl<*> = view(list, cached = true) {
    set(ORIENTATION, Orientation.Vertical)
    set(CELL_FACTORY) { ListEditorControl.DefaultCell { item -> item } }
    set(ADD_WITH_COMMA, false)
}

fun Layout.viewOrientableList(list: ListEditor<*, *>, multiline: Boolean): EditorControl<*> =
    if (multiline) viewVertical(list) else viewHorizontal(list)