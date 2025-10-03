package ponticello.model.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.PullResult
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.api.errors.JGitInternalException
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteConfig
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import ponticello.impl.Logger
import ponticello.model.project.Component
import ponticello.model.project.PonticelloProject
import reaktive.value.ReactiveBoolean
import reaktive.value.reactiveVariable
import java.io.File
import java.io.IOException

class ProjectGitRepository(private val git: Git) : ProjectVersionControl {
    private val _hasRemote = reactiveVariable(getRemote() != null)

    override val hasRemote: ReactiveBoolean
        get() = _hasRemote

    private fun getRemote(): RemoteConfig? {
        try {
            return git.remoteList()
                .call()
                .firstOrNull()
        } catch (e: GitAPIException) {
            Logger.error("Error getting remote: ${e.message}", e)
            return null
        }
    }

    override fun getRemoteUrl(): String? = getRemote()?.urIs?.firstOrNull()?.toString()

    override fun commitChanges(components: Set<Component<*>>, message: String): Boolean {
        val addCommand = git.add()
        for (component in components) {
            addCommand.addFilepattern(component.gitFilePattern)
        }
        try {
            addCommand.call()
        } catch (e: GitAPIException) {
            Logger.error("Error adding files to git repository: ${e.message}", e)
            return false
        }
        try {
            git.commit()
                .setMessage(message)
                .call()
        } catch (e: GitAPIException) {
            Logger.error("Error committing changes: ${e.message}", e)
            return false
        }
        return true
    }

    override fun pushToRemote(interaction: GitUserInteraction, onResult: (success: Boolean) -> Unit) =
        interaction.launch {
            val remote = getRemote()
            if (remote == null) {
                Logger.error("No remote defined")
                return@launch onResult(false)
            }
            try {
                git.push()
                    .setProgressMonitor(interaction)
                    .setRemote(remote.name)
                    .setRefSpecs(RefSpec("master"))
                    .setCredentialsProvider(Companion.getCredentials(interaction))
                    .call()
            } catch (e: GitAPIException) {
                Logger.error("Error pushing to $remote: ${e.message}", e)
                return@launch onResult(false)
            }
            onResult(true)
        }

    override fun pullFromRemote(interaction: GitUserInteraction, onResult: (result: PullResult?) -> Unit) =
        interaction.launch {
            val remote = getRemote()
            if (remote == null) {
                Logger.error("No remote defined")
                return@launch onResult(null)
            }
            try {
                val result = git.pull()
                    .setProgressMonitor(interaction)
                    .setRemote(remote.name)
                    .setCredentialsProvider(Companion.getCredentials(interaction))
                    .call()
                onResult(result)
            } catch (e: GitAPIException) {
                Logger.error("Error pulling from ${getRemote()}: ${e.message}", e)
                onResult(null)
            }
        }

    companion object {
        fun create(project: PonticelloProject): ProjectGitRepository? {
            val gitDir = project.projectDirectory.resolve(".git")
            check(!gitDir.isDirectory) { "Project ${project.name} already has a git repository" }

            val git = try {
                Git.init()
                    .setDirectory(project.projectDirectory)
                    .setGitDir(gitDir)
                    .setInitialBranch("master")
                    .call()
            } catch (e: GitAPIException) {
                Logger.error("Error initializing git repository: ${e.message}", e)
                return null
            }
            try {
                git.add()
                    .addFilepattern("project.pont")
                    .addFilepattern("data")
                    .addFilepattern("plugin_states")
//                                    .addFilepattern("samples")
                    .call()
            } catch (e: GitAPIException) {
                Logger.error("Error adding files to git repository for project ${project.name}: ${e.message}", e)
                return null
            } catch (e: JGitInternalException) {
                Logger.error(
                    "JGit internal error adding files to git repository for project ${project.name}: ${e.message}",
                    e
                )
                return null
            }
            try {
                git.commit().setMessage("Initial commit").call()
            } catch (e: GitAPIException) {
                Logger.error("Error during initial commit for project ${project.name}: ${e.message}", e)
                return null
            }
            return ProjectGitRepository(git)
        }

        fun get(directory: File): ProjectGitRepository? {
            val gitDir = directory.resolve(".git")
            if (!gitDir.isDirectory) return null
            val git = try {
                Git.open(directory)
            } catch (e: IOException) {
                Logger.error("Error reading git repository: ${e.message}", e)
                return null
            }
            return ProjectGitRepository(git)
        }

        suspend fun getCredentials(interaction: GitUserInteraction): CredentialsProvider? {
            val token = GitHubAuthentication.getToken(interaction::authenticate) ?: return null
            return UsernamePasswordCredentialsProvider("schleifenkauz", token)
        }
    }
}