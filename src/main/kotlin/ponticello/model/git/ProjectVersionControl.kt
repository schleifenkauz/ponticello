package ponticello.model.git

import org.eclipse.jgit.api.PullResult
import ponticello.model.project.Component
import reaktive.value.ReactiveBoolean

interface ProjectVersionControl {
    val hasRemote: ReactiveBoolean

    fun commitChanges(components: Set<Component<*>>, message: String): Boolean

    fun getRemoteUrl(): String?

    fun pushToRemote(interaction: GitUserInteraction, onResult: (success: Boolean) -> Unit)

    fun pullFromRemote(interaction: GitUserInteraction, onResult: (result: PullResult?) -> Unit)
}