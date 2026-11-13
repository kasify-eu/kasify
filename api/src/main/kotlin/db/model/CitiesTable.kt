package eu.kasify.db.model
import org.jetbrains.exposed.v1.core.Table

object CitiesTable : Table(name = "cities") {
    val id = uuid("id").autoGenerate()
    val countryId = reference("country_id", CountriesTable.id)
    val provinceId = reference("province_id", ProvincesTable.id)
    val name = text("name")
    val ineCode = varchar("ine_code", 5)
    val ineCodeMun = varchar("ine_code_mun", 3)

    override val primaryKey = PrimaryKey(id)
}
