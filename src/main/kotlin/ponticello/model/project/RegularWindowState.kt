package ponticello.model.project

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("RegularWindow")
class RegularWindowState(override val reference: Reference): WindowState()