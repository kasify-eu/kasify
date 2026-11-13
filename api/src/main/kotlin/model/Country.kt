package eu.kasify.model
import eu.kasify.db.model.CountriesTable
import io.perracodex.exposed.pagination.MapModel
import org.jetbrains.exposed.v1.core.ResultRow
import kotlin.uuid.Uuid

class Country(
    val id: Uuid,
    val name: String,
    val nameEs: String,
    val alpha2: String,
    val alpha3: String,
) {
    companion object : MapModel<Country> {
        override fun from(row: ResultRow): Country =
            Country(
                id = row[CountriesTable.id],
                name = row[CountriesTable.name],
                nameEs = row[CountriesTable.nameEs],
                alpha2 = row[CountriesTable.alpha2],
                alpha3 = row[CountriesTable.alpha3],
            )
    }
}
