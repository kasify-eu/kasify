package eu.kasify.db.model

import org.postgresql.util.PGobject

class PGEnum<T : Enum<T>>(
    enumTypeName: String,
    enumValue: T?,
) : PGobject() {
    init {
        value = enumValue?.name?.lowercase()
        type = enumTypeName
    }
}
