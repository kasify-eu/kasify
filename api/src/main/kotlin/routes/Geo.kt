package eu.kasify.routes

import eu.kasify.RateLimitProviders
import eu.kasify.db.GeoRepository
import eu.kasify.maybeRateLimit
import eu.kasify.model.api.responses.CitiesResponse
import eu.kasify.model.api.responses.CountriesResponse
import eu.kasify.model.api.responses.ProvincesResponse
import eu.kasify.routes.extensions.getPageableWithLimit
import eu.kasify.versionedRouting
import io.ktor.http.CacheControl
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.response.cacheControl
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

@OptIn(ExperimentalKtorApi::class)
fun Application.configureGeoRouting() {
    val geoRepository by inject<GeoRepository>()

    versionedRouting {
        // Public endpoints — no auth required (used by check-in form)
        maybeRateLimit(RateLimitProviders.IpGeo) {
            get("/geo/countries") {
                val query = call.parameters["query"]?.takeIf { it.isNotBlank() }
                val pageable = call.getPageableWithLimit(maxPageSize = 300)
                val countries = geoRepository.searchCountries(query = query, pageable = pageable)
                if (call.parameters.isEmpty()) {
                    call.response.cacheControl(CacheControl.MaxAge(maxAgeSeconds = 86400, visibility = CacheControl.Visibility.Public))
                }
                call.respond(CountriesResponse(countries))
            }.describe {
                tag("Geo")
                summary = "Search countries"
                description = "Public reference endpoint for the check-in form. Responses without query params are cacheable for 24 hours."
                parameters {
                    query("query") { description = "Free-text name filter (case-insensitive)" }
                    query("page") { description = "Zero-based page index (default 0)" }
                    query("size") { description = "Page size (max 300)" }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "Paginated countries"
                        schema = jsonSchema<CountriesResponse>()
                    }
                }
            }

            get("/geo/provinces") {
                val countryId = call.parameters["country_id"]?.let { Uuid.parse(it) }
                val query = call.parameters["query"]?.takeIf { it.isNotBlank() }
                val pageable = call.getPageableWithLimit()
                val provinces =
                    geoRepository.searchProvinces(
                        countryId = countryId,
                        query = query,
                        pageable = pageable,
                    )
                if (call.parameters.isEmpty()) {
                    call.response.cacheControl(CacheControl.MaxAge(maxAgeSeconds = 86400, visibility = CacheControl.Visibility.Public))
                }
                call.respond(ProvincesResponse(provinces))
            }.describe {
                tag("Geo")
                summary = "Search provinces"
                description = "Public reference endpoint. Scope results to a country with `country_id`. Responses without query params are cacheable for 24 hours."
                parameters {
                    query("country_id") { description = "Restrict provinces to this country (UUID)" }
                    query("query") { description = "Free-text name filter" }
                    query("page") { description = "Zero-based page index" }
                    query("size") { description = "Page size (max 100)" }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "Paginated provinces"
                        schema = jsonSchema<ProvincesResponse>()
                    }
                }
            }

            get("/geo/cities") {
                val provinceId = call.parameters["province_id"]?.let { Uuid.parse(it) }
                val query = call.parameters["query"]?.takeIf { it.isNotBlank() }
                val pageable = call.getPageableWithLimit()
                val cities =
                    geoRepository.searchCities(
                        provinceId = provinceId,
                        query = query,
                        pageable = pageable,
                    )
                call.respond(CitiesResponse(cities))
            }.describe {
                tag("Geo")
                summary = "Search cities"
                description = "Public reference endpoint. Scope results to a province with `province_id`."
                parameters {
                    query("province_id") { description = "Restrict cities to this province (UUID)" }
                    query("query") { description = "Free-text name filter" }
                    query("page") { description = "Zero-based page index" }
                    query("size") { description = "Page size (max 100)" }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "Paginated cities"
                        schema = jsonSchema<CitiesResponse>()
                    }
                }
            }
        }
    }
}
