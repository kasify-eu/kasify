package eu.kasify.db.model
import org.jetbrains.exposed.v1.core.Table

object ProvincesTable : Table(name = "provinces") {
    val id = uuid("id").autoGenerate()
    val countryId = reference("country_id", CountriesTable.id)
    val name = text("name")
    val codeIso31662 = varchar("code_iso_3166_2", 6)
    val ineCodePro = varchar("ine_code_pro", 2)

    override val primaryKey = PrimaryKey(id)
}
