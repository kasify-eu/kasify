package eu.kasify.plugins.unifi.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class UnifiLoginResponse(
    @SerialName("unique_id")
    val uniqueId: String,
    @SerialName("first_name")
    val firstName: String,
    @SerialName("last_name")
    val lastName: String,
    val alias: String,
    @SerialName("full_name")
    val fullName: String,
    val email: String,
    @SerialName("email_status")
    val emailStatus: String,
    @SerialName("email_is_null")
    val emailIsNull: Boolean,
    @SerialName("user_email")
    val userEmail: String,
    val phone: String,
    @SerialName("avatar_relative_path")
    val avatarRelativePath: String,
    @SerialName("avatar_rpath2")
    val avatarRpath2: String,
    val status: String,
    @SerialName("employee_number")
    val employeeNumber: String,
    @SerialName("create_time")
    val createTime: Long,
    @SerialName("login_time")
    val loginTime: Long,
    val extras: JsonObject,
    val username: String,
    @SerialName("local_account_exist")
    val localAccountExist: Boolean,
    @SerialName("password_revision")
    val passwordRevision: Long,
    @SerialName("sso_account")
    val ssoAccount: String,
    @SerialName("sso_uuid")
    val ssoUuid: String,
    @SerialName("sso_username")
    val ssoUsername: String,
    @SerialName("sso_picture")
    val ssoPicture: String,
    @SerialName("uid_sso_id")
    val uidSsoId: String,
    @SerialName("uid_sso_account")
    val uidSsoAccount: String,
    @SerialName("uid_account_status")
    val uidAccountStatus: String,
    val groups: List<JsonObject>,
    val roles: List<UnifiRole>,
    val permissions: Map<String, List<String>>,
    val scopes: List<String>,
    @SerialName("cloud_access_granted")
    val cloudAccessGranted: Boolean,
    @SerialName("only_local_account")
    val onlyLocalAccount: Boolean,
    @SerialName("update_time")
    val updateTime: Long,
    val avatar: String? = null,
    @SerialName("nfc_token")
    val nfcToken: String,
    @SerialName("nfc_display_id")
    val nfcDisplayId: String,
    @SerialName("nfc_card_type")
    val nfcCardType: String,
    @SerialName("nfc_card_status")
    val nfcCardStatus: String,
    @SerialName("org_user_id")
    val orgUserId: String,
    val role: String,
    val roleId: String,
    val id: String,
    val isOwner: Boolean,
    val isSuperAdmin: Boolean,
    val isMember: Boolean,
    val maskedEmail: String,
    val accessMask: Int,
    val permissionMask: Int,
    val deviceToken: String,
    val ssoAuth: JsonObject,
)

@Serializable
data class UnifiRole(
    @SerialName("unique_id")
    val uniqueId: String,
    val name: String,
    @SerialName("system_role")
    val systemRole: Boolean,
    @SerialName("system_key")
    val systemKey: String,
    val level: Int,
    @SerialName("create_time")
    val createTime: String,
    @SerialName("update_time")
    val updateTime: String,
    @SerialName("is_private")
    val isPrivate: Boolean,
)
