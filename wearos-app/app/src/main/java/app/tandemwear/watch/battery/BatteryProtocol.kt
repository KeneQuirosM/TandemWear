package app.tandemwear.watch.battery

import java.util.UUID

/**
 * Standard GATT Battery Service (Bluetooth SIG). Pure, unit-tested.
 * Confirmed on iOS 26 (21-10): 180f is published on our link and 2a19 is readable.
 */
object BatteryProtocol {

    val SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
    val LEVEL_UUID: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")

    /** Battery Level is one byte, 0..100 %. Anything else is not a level. */
    fun parseLevel(value: ByteArray): Int? {
        if (value.size != 1) return null
        val level = value[0].toInt() and 0xFF
        return level.takeIf { it in 0..100 }
    }

    private const val BASE_SUFFIX = "-0000-1000-8000-00805f9b34fb"

    /** "180f" for SIG 16-bit UUIDs, the full UUID otherwise. */
    fun shortName(uuid: UUID): String {
        val s = uuid.toString().lowercase()
        return if (s.startsWith("0000") && s.endsWith(BASE_SUFFIX)) s.substring(4, 8) else s
    }

    /** One diagnostics line with every service UUID, sorted, known ones named. */
    fun describeServices(uuids: Collection<UUID>, names: Map<UUID, String>): String =
        uuids.map { uuid -> shortName(uuid) + (names[uuid]?.let { "($it)" } ?: "") }.sorted().joinToString(", ")
}
