package ponticello.sc.client

interface SuperColliderListener {
    fun onMessage(path: String, content: String)
}