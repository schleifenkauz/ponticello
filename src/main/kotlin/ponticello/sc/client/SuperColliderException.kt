package ponticello.sc.client

import com.illposed.osc.OSCMessage

class SuperColliderException(
    val description: String?,
    val oscMessage: OSCMessage?,
    val errorMessage: String,
) : RuntimeException() {
    override val message: String
        get() =
            if (oscMessage == null) "SuperCollider error with unknown source:\n $errorMessage"
            else if (description != null) "SuperCollider error $description:\n $errorMessage"
            else "SuperCollider error with source ${oscMessage.address}, ${oscMessage.arguments}\n: $errorMessage"


}