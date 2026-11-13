package eu.kasify.model.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid
import eu.kasify.model.Province as DomainProvince

@Serializable
class Province(
    val id: Uuid,
    @SerialName("country_id")
    val countryId: Uuid,
    val name: String,
    @SerialName("code_iso_3166_2")
    val codeIso31662: String,
    @SerialName("ine_code_pro")
    val ineCodePro: String,
) {
    companion object {
        fun from(domain: DomainProvince): Province =
            Province(
                id = domain.id,
                countryId = domain.countryId,
                name = domain.name,
                codeIso31662 = domain.codeIso31662,
                ineCodePro = domain.ineCodePro,
            )
    }
}
