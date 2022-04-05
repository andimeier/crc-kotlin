package com.siemens.bmd.configapp.image

import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.quickbirdstudios.CRC16

/**
 * Object representing the config data of one device (e.g. one wireless sensor device)
 */
data class Device(
    val pof: configPof,

    // time when this config data has been read from the device,
    // thus representing the "up-to-date-ness" of the data
    var timestamp: DateTime, 
)

/**
 * Base class for config structures.
 * Includes packing/unpacking of fields. The last bytes of the data structure is
 * always assumed to be a CRC16 checksum. This will be checked/written automatically.
 * 
 * Basic assumptions about config structures:
 * * the first byte (UInt8) determines the structure version to be used for 
 *   packing/unpacking
 * * the last UInt16 is the CRC16 checksum
 */
open class ConfigStructure {
    /**
     * Pack the given fields (in the given order and layout) into the
     * passed ByteBuffer.
     * 
     * Only the fields will be stuffed into the ByteBuffer, no CRC will
     * be added.
     * 
     * @param buffer the byte buffer to be written to
     * @param fields the data field layout
     */
    fun packFields(buffer: ByteBuffer, fields: List<DataField>) {
        fields.forEach {
            it.writeTo(buffer)
        }
    }

    /**
     * Unpack the given fields (in the given order and layout) from the
     * passed ByteBuffer.
     * 
     * @param buffer the byte buffer to be read from
     * @param fields the data field layout for parsing the fields
     */
    fun unpackFields(buffer: ByteBuffer, fields: List<DataField>) {
        fields.forEach {
            it.getFrom(buffer)
        }
    }


    /**
     * Unpack the next bytes and interpret them as CRC value. Unpack the value
     * and return it.
     * 
     * @param buffer the byte buffer to be read from
     * @return the CRC16 value read from the byte buffer
     */
    fun unpackCrc16(buffer: ByteBuffer): UShort {
        return buffer.getShort().toUShort()
    }

    /**
     * Calculate CRC16 over the given ByteBuffer content. The buffer will be 
     * consumed.
     * 
     * @param buffer the ByteBuffer over which the CRC16 will be calculated
     * @return the calculated CRC16 value
     */
    fun calculateCrc16(buffer: ByteBuffer): UShort {
        return buffer.duplicate().let {
            it.flip()
            val arr = ByteArray(it.limit())
            it.get(arr)

            //val crc16 = CRC16()
            //crc16.update(arr)
            // crc16.value
            8721.toUShort() // FIXME das ist nur ein Dummy-CRC, hier gehoeren die oberen Zeilen wieder einkommentiert
        }
    }

    /**
     * Serialize the config structure.
     * 
     * @return the serialized structure, including the trailing CRC16 checksum
     */
    open fun pack(fields: List<DataField>): ByteArray {
        val buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN)

        packFields(buffer, fields)

        // add CRC checksum at the end of the buffer
        val crc = calculateCrc16(buffer)
        buffer.putShort(crc.toShort())

        return buffer.duplicate().let {
            it.flip()
            val arr = ByteArray(it.limit())
            it.get(arr)
            arr
        }
    }

    /**
     * Deserialize a byte buffer into the structure values.
     * 
     * This version of unpack determines the structure version to be used for
     * unpacking by fetching the version info at the beginning of the buffer.
     * 
     * @throws Exception on CRC error
     */
    open fun unpack(byteArray: ByteArray) {

        val buffer = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN)
        
        val version = getVersion(buffer)
        //val fields = getFields(version)
        val fields = listOf<DataField>() // FIXME noch nix drin, unimplemented yet

        unpack(buffer, fields)
    }

    /**
     * Deserialize a byte buffer into the structure values.
     * 
     * @throws Exception on CRC error
     */
    fun unpack(buffer: ByteBuffer, fields: List<DataField>) {
        unpackFields(buffer, fields)

        // get slice of buffer over which the CRC should be calculated (from position 0 to 
        // current position)
        val dataBuffer = buffer.duplicate().apply {
            flip()
        }
        val crcCalculated = calculateCrc16(dataBuffer)
        println("calculated CRC=${crcCalculated}")

        // read recorded CRC
        val crcRecorded = unpackCrc16(buffer)
        println("recorded CRC=${crcRecorded}")

        if (crcRecorded != crcCalculated) {
            throw Exception("checksum mismatch!")
        }
    }

    /**
     * Detect structure version by reading the first byte of the binary values.
     * 
     * The buffer position will not advance.
     * 
     * @param buffer the ByteBuffer containing the version byte at the current 
     *   buffer position
     */
    fun getVersion(buffer: ByteBuffer) : Int {
        val position = buffer.position()
        val version: Int = buffer.get().toInt()
        buffer.position(position) // reset position to where it was before
        return version
    }
}

/**
 * The interface for all data fields of a config structure.
 * 
 * This interface enables the fields to be serialized/deserialized
 * to/from a given ByteBuffer.
 */
interface DataField {

    /**
     * The name of this data field, used for output/logging.
     */
    abstract val name: String

    /**
     * Write the value of this data field to an existing ByteBuffer, modifying it.
     */
    abstract fun writeTo(buffer: ByteBuffer)

    /**
     * Retrieve the value of this data field by consuming the appropriate number
     * of bytes from the ByteBuffer and converting them into the data field value.
     */
    abstract fun getFrom(buffer: ByteBuffer)
}

/**
 * Class representing a UInt8 data field.
 */
class UInt8(override val name: String) : DataField {
    public var value: UByte? = null

    override fun writeTo(buffer: ByteBuffer) {
        if (value == null) {
            throw Exception("no value for field ${name} defined, cannot serialize it")
        }
        value?.let { buffer.put(it.toByte()) }
    }

    override fun getFrom(buffer: ByteBuffer) {
        value = buffer.get().toUByte()
    }
}

/**
 * Class representing a UInt16 data field.
 */
class UInt16(override val name: String) : DataField {
    public var value: UShort? = null

    override fun writeTo(buffer: ByteBuffer) {
        if (value == null) {
            throw Exception("no value for field ${name} defined, cannot serialize it")
        }
        value?.let { buffer.putShort(it.toShort()) }
    }

    override fun getFrom(buffer: ByteBuffer) {
        value = buffer.getShort().toUShort()
    }
}

/**
 * Class representing a UInt32 data field.
 */
class UInt32(override val name: String) : DataField {
    public var value: UInt? = null

    override fun writeTo(buffer: ByteBuffer) {
        if (value == null) {
            throw Exception("no value for field ${name} defined, cannot serialize it")
        }
        value?.let { buffer.putInt(it.toInt()) }
    }

    override fun getFrom(buffer: ByteBuffer) {
        value = buffer.getInt().toUInt()
    }
}

/**
 * Class representing a UInt64 data field.
 */
class UInt64(override val name: String) : DataField {
    public var value: ULong? = null

    override fun writeTo(buffer: ByteBuffer) {
        if (value == null) {
            throw Exception("no value for field ${name} defined, cannot serialize it")
        }
        value?.let { buffer.putLong(it.toLong()) }
    }

    override fun getFrom(buffer: ByteBuffer) {
        value = buffer.getLong().toULong()
    }
}

/**
 * A consecutive number of Null bytes, a.k.a. "reserved field"
 * 
 * On writing, it renders to 0x00 bytes. On reading, the actual
 * read bytes will be dismissed (ignored), except for contributing
 * to the checksum.
 */
 class NullBytes(override val name: String, val size: Int) : DataField {
     
    override fun writeTo(buffer: ByteBuffer) {
        val arr = ByteArray(size) { _ -> 0x00 }
        buffer.put(arr)
    }

    override fun getFrom(buffer: ByteBuffer) {
        // skip over "size" bytes
        buffer.position(buffer.position() + size);
    }
}

/**
 * The POF config structure of a wireless sensor node
 */
class ConfigPof (

    // FIXME Nulls erlauben? Falls zB eine Version der ConfigStruct bestimmte Felder
    // noch nicht kennt, sollten die ja null sein, oder? Damit das UI das 
    // visualisieren kann. Oder gibt es immer DefaultValues (so wie hier aktuell
    // implementiert)?
    var pofStructVersion: UInt8 = UInt8("pofStructVersion"),
    var hwNumber: UInt16 = UInt16("hwNumber"),
    var cpuSerial: UInt32 = UInt32("cpuSerial"),
    var crc: UInt16 = UInt16("crc"),


    // note that the trailing CRC16 will be added implicitly and must NOT be
    // included in the field list
    val configPofV1: List<DataField> = listOf<DataField>(
        NullBytes("reserved1", 1),
        pofStructVersion,
        NullBytes("reserved2", 2),
        hwNumber,
        cpuSerial,
    ),

    // note that the trailing CRC16 will be added implicitly and must NOT be
    // included in the field list
    val configPofV2: List<DataField> = listOf<DataField>(
        pofStructVersion,
        cpuSerial,
        hwNumber,
        NullBytes("reserved", 3),
    ),
    
) : ConfigStructure() {

    /**
     * For the time being, hardcode the configPof version to configPofV1
     * 
     * @throws Exception on CRC error
     */
    override fun unpack(byteArray: ByteArray) {
        val buffer = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN)
        super.unpack(buffer, configPofV1)
    }

    /**
     * For the time being, hardcode the configPof version to configPofV1
     */
    fun pack(): ByteArray {
        return super.pack(configPofV1)
    }

    override fun toString(): String {
        return "pofStructVersion=${pofStructVersion.value}, hwNumber=${hwNumber.value}, cpuSerial=${cpuSerial.value}"
    }
}


/**
 * Example for decoding a byte stream into a config object.
 */
fun decodeConfig() {
    val byteArray: ByteArray = ubyteArrayOf(0x00U, 0xA1U, 0x00U, 0x00U, 0x2EU, 0x38U, 0xD4U, 
        0x89U, 0xC8U, 0xA3U, 0x11U, 0x22U).toByteArray()


    // deserialize: read from bytes into the config values
    val configPof = ConfigPof().apply {
        unpack(byteArray)
    }


    // serialize: read from the config values into the serialized bytes
    val byteArray2 = configPof.pack()

    println("bytearray is: ")
    byteArray2.forEach {
        println("  ${it}")
    }
    
    println(configPof)

    println("cpuSerial is: ${configPof.cpuSerial.value}")
}

fun main() {
    decodeConfig()
}