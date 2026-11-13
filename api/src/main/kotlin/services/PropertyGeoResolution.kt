package eu.kasify.services

import eu.kasify.db.GeoRepository
import eu.kasify.model.api.requests.NewPropertyRequest
import eu.kasify.model.api.requests.PROPERTY_DEFAULT_COUNTRY_ALPHA3
import eu.kasify.routes.exceptions.BadParamRequestException
import kotlin.uuid.Uuid

data class PropertyGeoResolution(
    /** UUID stored in `properties.country_id`. */
    val countryId: Uuid,
    /** ISO 3166-1 alpha-2 code, echoed in the API response (`country` field). */
    val countryAlpha2: String,
    /** ISO 3166-1 alpha-3 code, echoed in the API response (`country_code` field). */
    val countryAlpha3: String,
    /** City name stored in `properties.city`; for Spain looked up from the `cities` row. */
    val cityName: String,
    val provinceId: Uuid?,
    val cityId: Uuid?,
)

/**
 * Resolves the country, province and city for a property create/update request.
 *
 * For Spanish properties (`country_code == ESP`), `province_id` and `city_id` are required and the
 * `cities` row's `name` is used as the canonical city text. For other countries the user-supplied
 * free-text city is used. Validation of presence is performed in [NewPropertyRequest.validate]
 * before this is called; this resolver only handles the database lookups (and rejects unknown
 * country / province / city ids).
 */
suspend fun resolvePropertyGeo(
    request: NewPropertyRequest,
    geoRepository: GeoRepository,
): PropertyGeoResolution {
    val resolvedAlpha3 = request.countryCode?.uppercase() ?: PROPERTY_DEFAULT_COUNTRY_ALPHA3
    val country =
        geoRepository.getCountryByAlpha3(resolvedAlpha3)
            ?: throw BadParamRequestException(
                "Unknown country_code: $resolvedAlpha3",
                code = "property.unknown_country",
            )

    if (resolvedAlpha3 == PROPERTY_DEFAULT_COUNTRY_ALPHA3) {
        val provinceId =
            request.provinceId
                ?: throw BadParamRequestException(
                    "province_id is required for Spanish properties",
                    code = "property.province_required",
                )
        val cityId =
            request.cityId
                ?: throw BadParamRequestException(
                    "city_id is required for Spanish properties",
                    code = "property.city_required",
                )

        val city =
            geoRepository.getCityById(cityId)
                ?: throw BadParamRequestException(
                    "Unknown city_id: $cityId",
                    code = "property.unknown_city",
                )
        if (city.provinceId != provinceId) {
            throw BadParamRequestException(
                "city_id does not belong to province_id",
                code = "property.city_province_mismatch",
            )
        }

        return PropertyGeoResolution(
            countryId = country.id,
            countryAlpha2 = country.alpha2,
            countryAlpha3 = country.alpha3,
            cityName = city.name,
            provinceId = provinceId,
            cityId = cityId,
        )
    }

    val cityText =
        request.city?.takeIf { it.isNotBlank() }
            ?: throw BadParamRequestException(
                "city is required for non-Spanish properties",
                code = "property.city_required",
            )

    return PropertyGeoResolution(
        countryId = country.id,
        countryAlpha2 = country.alpha2,
        countryAlpha3 = country.alpha3,
        cityName = cityText,
        provinceId = null,
        cityId = null,
    )
}
