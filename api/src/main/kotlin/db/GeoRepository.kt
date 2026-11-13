package eu.kasify.db
import eu.kasify.db.model.CitiesTable
import eu.kasify.db.model.CountriesTable
import eu.kasify.db.model.ProvincesTable
import eu.kasify.model.City
import eu.kasify.model.Country
import eu.kasify.model.Province
import io.perracodex.exposed.pagination.Page
import io.perracodex.exposed.pagination.Pageable
import io.perracodex.exposed.pagination.paginate
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.Uuid

private fun unaccent(expr: Expression<String>): CustomFunction<String> = CustomFunction("unaccent", TextColumnType(), expr)

private fun Column<String>.ilikeUnaccent(pattern: String): Op<Boolean> = unaccent(this.lowerCase()) like pattern

class GeoRepository {
    suspend fun searchCountries(
        query: String? = null,
        pageable: Pageable? = null,
    ): Page<Country> =
        dbQuery {
            val q = CountriesTable.selectAll()

            if (!query.isNullOrBlank()) {
                val pattern = "%${escapeLike(query.trim().lowercase())}%"
                q.where {
                    (CountriesTable.name.ilikeUnaccent(pattern)) or
                        (CountriesTable.nameEs.ilikeUnaccent(pattern)) or
                        (CountriesTable.alpha2.lowerCase() like pattern) or
                        (CountriesTable.alpha3.lowerCase() like pattern)
                }
            }

            q.paginate(pageable = pageable, map = Country.Companion)
        }

    suspend fun getCountryByAlpha3(alpha3: String): Country? =
        dbQuery {
            CountriesTable
                .selectAll()
                .where { CountriesTable.alpha3 eq alpha3.uppercase() }
                .firstNotNullOfOrNull { row -> Country.from(row) }
        }

    suspend fun getCountryByAlpha2(alpha2: String): Country? =
        dbQuery {
            CountriesTable
                .selectAll()
                .where { CountriesTable.alpha2 eq alpha2.uppercase() }
                .firstNotNullOfOrNull { row -> Country.from(row) }
        }

    suspend fun getCountryById(id: Uuid): Country? =
        dbQuery {
            CountriesTable
                .selectAll()
                .where { CountriesTable.id eq id }
                .firstNotNullOfOrNull { row -> Country.from(row) }
        }

    suspend fun getCountriesByIds(ids: Set<Uuid>): Map<Uuid, Country> {
        if (ids.isEmpty()) return emptyMap()
        return dbQuery {
            CountriesTable
                .selectAll()
                .where { CountriesTable.id inList ids }
                .associate { row -> row[CountriesTable.id] to Country.from(row) }
        }
    }

    suspend fun getProvincesByIds(ids: Set<Uuid>): Map<Uuid, Province> {
        if (ids.isEmpty()) return emptyMap()
        return dbQuery {
            ProvincesTable
                .selectAll()
                .where { ProvincesTable.id inList ids }
                .associate { row -> row[ProvincesTable.id] to Province.from(row) }
        }
    }

    suspend fun getCitiesByIds(ids: Set<Uuid>): Map<Uuid, City> {
        if (ids.isEmpty()) return emptyMap()
        return dbQuery {
            CitiesTable
                .selectAll()
                .where { CitiesTable.id inList ids }
                .associate { row -> row[CitiesTable.id] to City.from(row) }
        }
    }

    suspend fun getCityById(id: Uuid): City? =
        dbQuery {
            CitiesTable
                .selectAll()
                .where { CitiesTable.id eq id }
                .firstNotNullOfOrNull { row -> City.from(row) }
        }

    suspend fun searchProvinces(
        countryId: Uuid? = null,
        query: String? = null,
        pageable: Pageable? = null,
    ): Page<Province> =
        dbQuery {
            val q = ProvincesTable.selectAll()
            var condition: Op<Boolean>? =
                countryId?.let {
                    ProvincesTable.countryId eq it
                }

            if (!query.isNullOrBlank()) {
                val pattern = "%${escapeLike(query.trim().lowercase())}%"
                val nameCondition = ProvincesTable.name.ilikeUnaccent(pattern)
                condition = condition?.and(nameCondition) ?: nameCondition
            }

            if (condition != null) {
                q.where { condition }
            }

            q.paginate(pageable = pageable, map = Province.Companion)
        }

    suspend fun searchCities(
        provinceId: Uuid? = null,
        query: String? = null,
        pageable: Pageable? = null,
    ): Page<City> =
        dbQuery {
            val q = CitiesTable.selectAll()
            var condition: Op<Boolean>? =
                provinceId?.let {
                    CitiesTable.provinceId eq it
                }

            if (!query.isNullOrBlank()) {
                val pattern = "%${escapeLike(query.trim().lowercase())}%"
                val nameCondition = CitiesTable.name.ilikeUnaccent(pattern)
                condition = condition?.and(nameCondition) ?: nameCondition
            }

            if (condition != null) {
                q.where { condition }
            }

            q.paginate(pageable = pageable, map = City.Companion)
        }
}
