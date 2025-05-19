package ponticello.sc.client

interface OSCListener {
    fun onMessage(path: String, content: String)
}