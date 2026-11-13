package eu.kasify.model
import eu.kasify.db.model.CitiesTable
import io.perracodex.exposed.pagination.MapModel
import org.jetbrains.exposed.v1.core.ResultRow
import kotlin.uuid.Uuid

class City(
    val id: Uuid,
    val countryId: Uuid,
    val provinceId: Uuid,
    val name: String,
    val ineCode: String,
    val ineCodeMun: String,
) {
    companion object : MapModel<City> {
        override fun from(row: ResultRow): City =
            City(
                id = row[CitiesTable.id],
                countryId = row[CitiesTable.countryId],
                provinceId = row[CitiesTable.provinceId],
                name = row[CitiesTable.name],
                ineCode = row[CitiesTable.ineCode],
                ineCodeMun = row[CitiesTable.ineCodeMun],
            )
    }
}
