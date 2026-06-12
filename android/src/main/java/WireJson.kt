// Jackson mapper aligned with the wire format of the typeshare-generated
// types in HealthTypes.generated.kt.
//
// Tauri's Android bridge speaks Jackson (plain databind), while typeshare
// emits kotlinx.serialization annotations — but no kotlinx runtime is
// needed: every generated enum carries its wire name in a `string`
// property, which this mapper uses for both directions. Nulls are omitted
// to match serde's `skip_serializing_if = "Option::is_none"`.

package app.tauri.health

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.DeserializationConfig
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationConfig
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier
import com.fasterxml.jackson.module.kotlin.kotlinModule
import java.util.concurrent.ConcurrentHashMap

object WireJson {
    private val wireNamesCache = ConcurrentHashMap<Class<*>, Map<Enum<*>, String>>()

    /** Enum constant → wire name, via the generated `string` property. */
    private fun wireNames(cls: Class<*>): Map<Enum<*>, String> =
        wireNamesCache.getOrPut(cls) {
            val getString = try {
                cls.getMethod("getString")
            } catch (_: NoSuchMethodException) {
                null
            }
            (cls.enumConstants ?: emptyArray()).associate { constant ->
                val entry = constant as Enum<*>
                entry to ((getString?.invoke(entry) as? String) ?: entry.name)
            }
        }

    private val wireEnumModule = SimpleModule().apply {
        setSerializerModifier(object : BeanSerializerModifier() {
            override fun modifyEnumSerializer(
                config: SerializationConfig,
                valueType: JavaType,
                beanDesc: BeanDescription,
                serializer: JsonSerializer<*>,
            ): JsonSerializer<*> = object : JsonSerializer<Enum<*>>() {
                override fun serialize(
                    value: Enum<*>,
                    gen: JsonGenerator,
                    serializers: SerializerProvider,
                ) {
                    gen.writeString(wireNames(value.javaClass).getValue(value))
                }
            }
        })
        setDeserializerModifier(object : BeanDeserializerModifier() {
            override fun modifyEnumDeserializer(
                config: DeserializationConfig,
                type: JavaType,
                beanDesc: BeanDescription,
                deserializer: JsonDeserializer<*>,
            ): JsonDeserializer<*> = object : JsonDeserializer<Enum<*>>() {
                override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Enum<*> {
                    val text = p.valueAsString
                    return wireNames(type.rawClass).entries
                        .firstOrNull { it.value == text }?.key
                        ?: throw ctxt.weirdStringException(
                            text, type.rawClass, "unknown wire enum value",
                        )
                }
            }
        })
    }

    val mapper: ObjectMapper = ObjectMapper()
        .registerModule(kotlinModule())
        .registerModule(wireEnumModule)
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)

    inline fun <reified T> parse(json: String): T = mapper.readValue(json, T::class.java)

    fun stringify(value: Any): String = mapper.writeValueAsString(value)
}
