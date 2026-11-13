package eu.kasify.model.api

import eu.kasify.db.model.BookingChannel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
class BookingInfo(
    val id: Uuid,
    @VisibleTo(Visibility.OWNER)
    val channelId: String? = null,
    @SerialName("guest_first_name")
    val guestFirstName: String? = null,
    @VisibleTo(Visibility.MANAGER)
    @SerialName("guest_last_name")
    val guestLastName: String? = null,
    @SerialName("adults_count")
    val adultsCount: Int? = null,
    @SerialName("children_count")
    val childrenCount: Int? = null,
    @SerialName("infants_count")
    val infantsCount: Int? = null,
    @VisibleTo(Visibility.MANAGER)
    val channel: BookingChannel,
)
