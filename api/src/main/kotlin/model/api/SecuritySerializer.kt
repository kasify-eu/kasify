package eu.kasify.model.api

import eu.kasify.db.model.PropertyAccessLevel
import eu.kasify.services.AccessInfo
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.lang.reflect.ParameterizedType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.uuid.Uuid

/**
 * Utility for role-based field filtering in API responses.
 *
 * Provides recursive filtering of nested objects based on @VisibleTo annotations.
 * Each field is filtered according to the user's visibility level derived from AccessInfo.
 *
 * Usage:
 * ```
 * val access = authorizationService.getAccess(user, property)
 * val filtered = SecuritySerializer.serialize(booking, access)
 * call.respond(filtered)
 * ```
 */
object SecuritySerializer {
    /**
     * JSON instance configured for serialization.
     */
    @OptIn(ExperimentalSerializationApi::class)
    @PublishedApi
    internal val json =
        Json {
            encodeDefaults = true
            explicitNulls = false
        }

    /**
     * Cache of visible field names per (class, visibility level) to avoid repeated reflection.
     * Key: Pair<Class, Visibility> -> Set<field names visible at that level>
     */
    private val visibleFieldsCache = mutableMapOf<Pair<Class<*>, Visibility>, Set<String>>()

    /**
     * Cache of nested field types per class.
     * Maps JSON key name -> Class of the nested type
     */
    private val nestedTypesCache = mutableMapOf<Class<*>, Map<String, Class<*>>>()

    /**
     * Serialize data with role-based field filtering.
     * Recursively filters nested objects based on @VisibleTo annotations.
     */
    inline fun <reified T : Any> serialize(
        data: T,
        access: AccessInfo,
    ): JsonElement {
        val visibility = access.toVisibility()
        return filterRecursively(
            json = json.encodeToJsonElement(data).jsonObject,
            clazz = T::class.java,
            maxVisibility = visibility,
        )
    }

    /**
     * Serialize a list of items with role-based field filtering.
     */
    inline fun <reified T : Any> serialize(
        data: List<T>,
        access: AccessInfo,
    ): JsonArray {
        val visibility = access.toVisibility()
        return JsonArray(
            data.map { item ->
                filterRecursively(
                    json = json.encodeToJsonElement(item).jsonObject,
                    clazz = T::class.java,
                    maxVisibility = visibility,
                )
            },
        )
    }

    /**
     * Serialize a list where each item may have different access levels
     * based on its property relationship.
     *
     * @param items List of items to serialize
     * @param getPropertyId Function to extract property ID from each item
     * @param accessByProperty Map of property ID to AccessInfo
     */
    inline fun <reified T : Any> serializeWithPropertyAccess(
        items: List<T>,
        getPropertyId: (T) -> Uuid,
        accessByProperty: Map<Uuid, AccessInfo>,
    ): JsonArray =
        JsonArray(
            items.map { item ->
                val propertyId = getPropertyId(item)
                val access =
                    accessByProperty[propertyId]
                        ?: AccessInfo(hasAccess = true, role = "crew", isAccountMember = false)
                val visibility = access.toVisibility()
                filterRecursively(
                    json = json.encodeToJsonElement(item).jsonObject,
                    clazz = T::class.java,
                    maxVisibility = visibility,
                )
            },
        )

    /**
     * Recursively filter JSON object based on visibility annotations.
     */
    @PublishedApi
    internal fun filterRecursively(
        json: JsonObject,
        clazz: Class<*>,
        maxVisibility: Visibility,
    ): JsonObject {
        val visibleFields = getVisibleFields(clazz, maxVisibility)
        val nestedTypes = getNestedFieldTypes(clazz)

        return JsonObject(
            json
                .mapNotNull { (key, value) ->
                    // Skip fields not visible at this level
                    if (key !in visibleFields) return@mapNotNull null

                    val filtered =
                        when {
                            // Handle nested objects
                            value is JsonObject && key in nestedTypes -> {
                                filterRecursively(value, nestedTypes[key]!!, maxVisibility)
                            }
                            // Handle arrays of objects
                            value is JsonArray && key in nestedTypes -> {
                                JsonArray(
                                    value.mapNotNull { element ->
                                        when (element) {
                                            is JsonObject -> filterRecursively(element, nestedTypes[key]!!, maxVisibility)
                                            else -> element
                                        }
                                    },
                                )
                            }
                            // Primitive values pass through
                            else -> value
                        }
                    key to filtered
                }.toMap(),
        )
    }

    /**
     * Get field names visible at the given visibility level.
     * Uses @SerialName if present, otherwise the property name.
     * Results are cached for performance.
     */
    private fun getVisibleFields(
        clazz: Class<*>,
        maxVisibility: Visibility,
    ): Set<String> =
        visibleFieldsCache.getOrPut(clazz to maxVisibility) {
            clazz.kotlin.memberProperties
                .filter { prop ->
                    val annotation = prop.findAnnotation<VisibleTo>()
                    val requiredLevel = annotation?.level ?: Visibility.PUBLIC
                    // Field is visible if user's level >= required level
                    maxVisibility.ordinal >= requiredLevel.ordinal
                }.map { prop ->
                    // Use @SerialName value if present, otherwise property name
                    prop.findAnnotation<SerialName>()?.value ?: prop.name
                }.toSet()
        }

    /**
     * Get nested field types for a class.
     * Maps JSON key name -> Class of the nested type
     */
    private fun getNestedFieldTypes(clazz: Class<*>): Map<String, Class<*>> =
        nestedTypesCache.getOrPut(clazz) {
            clazz.kotlin.memberProperties
                .mapNotNull { prop ->
                    val jsonKey = prop.findAnnotation<SerialName>()?.value ?: prop.name
                    val returnType = prop.returnType
                    val classifier = returnType.classifier

                    // Get the Java class of the property
                    val javaField =
                        try {
                            clazz.getDeclaredField(prop.name)
                        } catch (e: NoSuchFieldException) {
                            // Try backing field
                            try {
                                clazz.getDeclaredField("${prop.name}\$delegate")
                            } catch (e2: NoSuchFieldException) {
                                null
                            }
                        }

                    val fieldClass =
                        when {
                            // For List<T>, get the element type T
                            javaField?.genericType is ParameterizedType -> {
                                val paramType = javaField.genericType as ParameterizedType
                                val rawType = paramType.rawType as? Class<*>
                                if (rawType == List::class.java || rawType == MutableList::class.java) {
                                    val typeArg = paramType.actualTypeArguments.firstOrNull()
                                    if (typeArg is Class<*> && !isPrimitive(typeArg)) typeArg else null
                                } else if (!isPrimitive(rawType)) {
                                    rawType
                                } else {
                                    null
                                }
                            }
                            // For non-parameterized types
                            javaField?.type != null && !isPrimitive(javaField.type) -> {
                                javaField.type
                            }

                            else -> null
                        }

                    if (fieldClass != null) jsonKey to fieldClass else null
                }.toMap()
        }

    /**
     * Check if a class is a primitive or basic type that doesn't need recursive filtering.
     */
    private fun isPrimitive(clazz: Class<*>?): Boolean {
        if (clazz == null) return true
        return clazz.isPrimitive ||
            clazz == String::class.java ||
            clazz == Int::class.java ||
            clazz == Long::class.java ||
            clazz == Double::class.java ||
            clazz == Float::class.java ||
            clazz == Boolean::class.java ||
            clazz == Char::class.java ||
            clazz == Byte::class.java ||
            clazz == Short::class.java ||
            Number::class.java.isAssignableFrom(clazz) ||
            clazz.isEnum ||
            clazz.name.startsWith("java.") ||
            clazz.name.startsWith("kotlin.") ||
            clazz.name.startsWith("kotlinx.datetime")
    }
}

/**
 * Convert AccessInfo to Visibility level.
 */
fun AccessInfo.toVisibility(): Visibility =
    when {
        isAccountMember -> Visibility.OWNER
        accessLevel == PropertyAccessLevel.Manager -> Visibility.MANAGER
        accessLevel == PropertyAccessLevel.Staff -> Visibility.STAFF
        accessLevel == PropertyAccessLevel.Contractor -> Visibility.CONTRACTOR
        else -> Visibility.CONTRACTOR
    }
