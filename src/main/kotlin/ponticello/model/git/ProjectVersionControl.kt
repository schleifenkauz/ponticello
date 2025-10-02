package ponticello.model.git

import org.eclipse.jgit.api.PullResult
import org.eclipse.jgit.lib.ProgressMonitor
import ponticello.model.project.Component
import reaktive.value.ReactiveBoolean

interface ProjectVersionControl {
    fun commitChanges(components: Set<Component<*>>, message: String): Boolean

    val hasRemote: ReactiveBoolean

    fun getRemoteUrl(): String

    fun pushToRemote(monitor: ProgressMonitor? = null): Boolean

    fun pullFromRemote(monitor: ProgressMonitor? = null): PullResult?
}