package eu.kasify.model.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid
import eu.kasify.model.Country as DomainCountry

@Serializable
class Country(
    val id: Uuid,
    val name: String,
    @SerialName("name_es")
    val nameEs: String,
    @SerialName("alpha_2")
    val alpha2: String,
    @SerialName("alpha_3")
    val alpha3: String,
) {
    companion object {
        fun from(domain: DomainCountry): Country =
            Country(
                id = domain.id,
                name = domain.name,
                nameEs = domain.nameEs,
                alpha2 = domain.alpha2,
                alpha3 = domain.alpha3,
            )
    }
}
