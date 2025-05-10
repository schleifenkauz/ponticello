package ponticello.ui.actions

import hextant.context.Context
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveValue
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveValue
import ponticello.ui.score.ScoreObjectSelectionManager
import ponticello.ui.score.ScoreObjectView

interface ObjectActionContext {
    val context: Context

    val focusedView: ReactiveValue<ScoreObjectView?>

    val selectedViews: Collection<ScoreObjectView>

    fun isApplicable(action: String): ReactiveBoolean

    class SingleObjectContext(private val view: ScoreObjectView) : ObjectActionContext {
        override val context: Context
            get() = view.context
        override val focusedView: ReactiveValue<ScoreObjectView?>
            get() = reactiveValue(view)
        override val selectedViews: Collection<ScoreObjectView>
            get() = listOf(view)

        override fun isApplicable(action: String): ReactiveBoolean = reactiveValue(true)
    }

    class SingleSelectedObjectContext(private val selector: ScoreObjectSelectionManager) : ObjectActionContext {
        override val focusedView: ReactiveValue<ScoreObjectView?>
            get() = selector.focusedView

        override val context: Context
            get() = selector.context

        override val selectedViews: Collection<ScoreObjectView>
            get() = selector.focusedView.now?.let(::listOf) ?: emptyList()

        override fun isApplicable(action: String): ReactiveBoolean = focusedView.map { v -> v != null }
    }

    class MultiObjectContext(private val selector: ScoreObjectSelectionManager) : ObjectActionContext {
        override val focusedView: ReactiveValue<ScoreObjectView?>
            get() = selector.focusedView

        override val context: Context
            get() = selector.context

        override val selectedViews: Collection<ScoreObjectView>
            get() = selector.selectedViews

        override fun isApplicable(action: String): ReactiveBoolean = reactiveValue(true)
    }
}