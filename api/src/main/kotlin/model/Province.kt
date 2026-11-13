package eu.kasify.model
import eu.kasify.db.model.ProvincesTable
import io.perracodex.exposed.pagination.MapModel
import org.jetbrains.exposed.v1.core.ResultRow
import kotlin.uuid.Uuid

class Province(
    val id: Uuid,
    val countryId: Uuid,
    val name: String,
    val codeIso31662: String,
    val ineCodePro: String,
) {
    companion object : MapModel<Province> {
        override fun from(row: ResultRow): Province =
            Province(
                id = row[ProvincesTable.id],
                countryId = row[ProvincesTable.countryId],
                name = row[ProvincesTable.name],
                codeIso31662 = row[ProvincesTable.codeIso31662],
                ineCodePro = row[ProvincesTable.ineCodePro],
            )
    }
}
