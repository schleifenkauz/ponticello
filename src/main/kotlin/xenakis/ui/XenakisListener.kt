package xenakis.ui

import xenakis.model.XenakisProject

interface XenakisListener {
    fun displayProject(project: XenakisProject)
    fun displayStartupScreen()
}