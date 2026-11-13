package eu.kasify.model.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid
import eu.kasify.model.City as DomainCity

@Serializable
class City(
    val id: Uuid,
    @SerialName("country_id")
    val countryId: Uuid,
    @SerialName("province_id")
    val provinceId: Uuid,
    val name: String,
    @SerialName("ine_code")
    val ineCode: String,
    @SerialName("ine_code_mun")
    val ineCodeMun: String,
) {
    companion object {
        fun from(domain: DomainCity): City =
            City(
                id = domain.id,
                countryId = domain.countryId,
                provinceId = domain.provinceId,
                name = domain.name,
                ineCode = domain.ineCode,
                ineCodeMun = domain.ineCodeMun,
            )
    }
}
