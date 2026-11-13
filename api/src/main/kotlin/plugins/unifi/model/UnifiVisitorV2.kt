package eu.kasify.plugins.unifi.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UnifiVisitorV2(
    @SerialName("unique_id")
    val uniqueId: String,
    @SerialName("inviter_id")
    val inviterId: String,
    @SerialName("inviter_name")
    val inviterName: String,
    @SerialName("first_name")
    val firstName: String,
    @SerialName("last_name")
    val lastName: String,
    val avatar: String = "",
    val remarks: String = "",
    @SerialName("mobile_phone")
    val mobilePhone: String = "",
    @SerialName("area_code")
    val areaCode: String = "",
    @SerialName("phone_number")
    val phoneNumber: String = "",
    val email: String = "",
    @SerialName("visitor_company")
    val visitorCompany: String = "",
    @SerialName("start_time")
    val startTime: Long,
    @SerialName("end_time")
    val endTime: Long,
    val timezone: String,
    @SerialName("schedule_id")
    val scheduleId: String? = null,
    val schedule: UnifiScheduleV2? = null,
    @SerialName("visit_reason")
    val visitReason: String,
    @SerialName("location_id")
    val locationId: String = "",
    @SerialName("nfc_cards")
    val nfcCards: List<String> = emptyList(),
    @SerialName("pin_code")
    val pinCode: UnifiPinCode? = null,
    @SerialName("has_qr_code")
    val hasQrCode: Boolean = false,
    @SerialName("license_plates")
    val licensePlates: List<String> = emptyList(),
    val resource: List<UnifiVisitorResourceV2> = emptyList(),
    val status: Int,
    @SerialName("managed_by_other")
    val managedByOther: Boolean = false,
    @SerialName("create_time")
    val createTime: Long,
    @SerialName("is_send_email")
    val isSendEmail: Boolean = false,
    @SerialName("is_send_sms")
    val isSendSms: Boolean = false,
    @SerialName("visit_time")
    val visitTime: Long? = null,
    @SerialName("is_print")
    val isPrint: Boolean = false,
    @SerialName("id_card")
    val idCard: String = "",
    @SerialName("photo_path")
    val photoPath: String = "",
    @SerialName("check_in_at")
    val checkInAt: Long = 0,
    @SerialName("require_check_in")
    val requireCheckIn: Boolean = false,
)

@Serializable
data class UnifiPinCode(
    @SerialName("user_asset_unique_id")
    val userAssetUniqueId: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("user_type")
    val userType: String,
    @SerialName("pin_code_token")
    val pinCodeToken: String,
    @SerialName("pin_code_length")
    val pinCodeLength: Int,
)

@Serializable
data class UnifiScheduleV2(
    val name: String,
    @SerialName("unique_id")
    val uniqueId: String,
    val type: String,
    @SerialName("is_default")
    val isDefault: Boolean = false,
    @SerialName("is_private")
    val isPrivate: Boolean = false,
    @SerialName("schedule_type")
    val scheduleType: String? = null,
)

@Serializable
data class UnifiVisitorResourceV2(
    @SerialName("resource_type")
    val resourceType: String,
    @SerialName("resource_value")
    val resourceValue: String,
    val name: String,
    @SerialName("group_info")
    val groupInfo: UnifiVisitorGroupInfo? = null,
)

@Serializable
data class UnifiVisitorGroupInfo(
    @SerialName("group_name")
    val groupName: String,
    @SerialName("group_type")
    val groupType: String,
    @SerialName("group_id")
    val groupId: String,
    @SerialName("group_configs")
    val groupConfigs: List<UnifiGroupConfig> = emptyList(),
    @SerialName("group_resources")
    val groupResources: List<UnifiGroupResourceItem>,
)

@Serializable
data class UnifiGroupConfig(
    val key: String,
    val value: String,
)
