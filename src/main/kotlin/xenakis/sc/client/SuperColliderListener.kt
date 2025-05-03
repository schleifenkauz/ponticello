package xenakis.sc.client

interface SuperColliderListener {
    fun onMessage(path: String, content: String)
}