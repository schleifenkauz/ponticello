package ponticello.ui.vc

import fxutils.actions.Action
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.materialdesign2.MaterialDesignE
import org.kordamp.ikonli.materialdesign2.MaterialDesignG
import ponticello.impl.Logger
import ponticello.impl.Platform
import ponticello.model.project.PonticelloProject
import reaktive.value.binding.equalTo
import reaktive.value.binding.impl.notNull
import reaktive.value.now
import reaktive.value.reactiveValue

object VersionControlActions : Action.Collector<PonticelloProject>({
    addAction("Initialize version control") {
        icon(MaterialDesignG.GIT)
        enableWhen { project -> project.versionControl.equalTo(null) }
        ifNotApplicable(Action.IfNotApplicable.Hide)
        executes { project ->
            project.save()
            val repo = project.createGitRepository()
            if (repo != null) {
                Logger.confirm("Version control initialized successfully!", Logger.Category.VersionControl)
            } else {
                Logger.error("Version control initialization failed!", Logger.Category.VersionControl)
            }
        }
    }
    addAction("Open GitHub Desktop") {
        enableWhen { project ->
            if (!Platform.get().isCommandAvailable("github-desktop")) reactiveValue(false)
            else project.versionControl.notNull()
        }
        ifNotApplicable(Action.IfNotApplicable.Hide)
        icon(MaterialDesignG.GITHUB)
        executes { project ->
            Platform.get().runCommand("github-desktop", "-p", project.projectDirectory.absolutePath)
        }
    }

    addAction("Commit changes") {
        shortcut("Ctrl+K")
        icon(Codicons.GIT_COMMIT)
        enableWhen { project -> project.versionControl.notNull() }
        ifNotApplicable(Action.IfNotApplicable.Hide)
        executes { project, ev ->
            val vc = project.versionControl.now ?: return@executes
            CommitPrompt(project, vc).showDialog(ev)
        }
    }
    addAction("Push changes") {
        shortcut("Ctrl+Shift+K")
        icon(MaterialDesignE.EXPORT_VARIANT)
        executes { project ->
            val vc = project.versionControl.now ?: return@executes
            vc.pushToRemote(JavaFXGitUserInteraction) { success ->
                if (success) {
                    Logger.confirm("Push successful!", Logger.Category.VersionControl)
                } else {
                    Logger.error("Push failed!", Logger.Category.VersionControl)
                }
            }
        }
    }
})