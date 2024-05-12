package xenakis.ui

import xenakis.model.XenakisProject

interface XenakisListener {
    fun superColliderReady()
    fun displayProject(project: XenakisProject)
    fun displayStartupScreen()
}