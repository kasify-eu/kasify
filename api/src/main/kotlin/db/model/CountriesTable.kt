package eu.kasify.db.model
import org.jetbrains.exposed.v1.core.Table

object CountriesTable : Table(name = "countries") {
    val id = uuid("id").autoGenerate()
    val name = text("name")
    val nameEs = text("name_es")
    val alpha2 = char("alpha_2", length = 2)
    val alpha3 = char("alpha_3", length = 3)

    override val primaryKey = PrimaryKey(id)
}
