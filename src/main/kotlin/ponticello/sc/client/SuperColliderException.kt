package ponticello.sc.client

class SuperColliderException(val errorMessage: String) : RuntimeException("Error in SuperCollider: $errorMessage")