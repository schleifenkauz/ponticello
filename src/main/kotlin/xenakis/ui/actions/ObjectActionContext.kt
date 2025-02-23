package xenakis.ui.actions

import hextant.context.Context
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveValue
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.ui.score.ScoreObjectSelectionManager
import xenakis.ui.score.ScoreObjectView

interface ObjectActionContext {
    val context: Context

    val focusedView: ReactiveValue<ScoreObjectView?>

    val selectedViews: Collection<ScoreObjectView>

    fun isApplicable(action: String): ReactiveBoolean

    class SingleObjectContext(private val selector: ScoreObjectSelectionManager) : ObjectActionContext {
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