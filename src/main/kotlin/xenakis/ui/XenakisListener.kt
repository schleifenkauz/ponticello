package xenakis.ui

import xenakis.model.XenakisProject

interface XenakisListener {
    fun readyToPlay()

    fun waitingForBoot()

    fun displayProject(project: XenakisProject)

    fun displayStartupScreen()
}