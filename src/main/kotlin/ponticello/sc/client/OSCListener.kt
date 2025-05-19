package ponticello.sc.client

interface OSCListener {
    fun onMessage(path: String, id: Int, content: String)
}