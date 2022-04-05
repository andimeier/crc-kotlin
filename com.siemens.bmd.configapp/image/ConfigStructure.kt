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
    fun packFields(val buffer: ByteBuffer, val fields: List<DataField>) {
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
    fun unpackFields(val buffer: ByteBuffer, val fields: List<DataField>) {
        fields.forEach {
            it.readFrom(buffer)
        }
    }


    /**
     * Unpack the next bytes and interpret them as CRC value. Unpack the value
     * and return it.
     * 
     * @param buffer the byte buffer to be read from
     * @return the CRC16 value read from the byte buffer
     */
    fun unpackCrc16(val buffer: ByteBuffer): UShort {
        return buffer.getShort().toUShort()
    }

    /**
     * Calculate CRC16 over the given ByteBuffer content. The buffer will be 
     * consumed.
     * 
     * @param buffer the ByteBuffer over which the CRC16 will be calculated
     * @return the calculated CRC16 value
     */
    fun calculateCrc16(val buffer: ByteBuffer): UShort {
        val arr = ByteArray(buffer.remaining())
        buffer.get(arr)

        // determine CRC16
        val crc16 = CRC16()
        crc16.update(arr)
        return crc16.value
    }

    /**
     * Serialize the config structure.
     */
    fun pack(val fields: List<DataField>): ByteBuffer {
        val buffer = ByteBuffer().order(ByteOrder.LITTLE_ENDIAN)

        packFields(buffer, fields)
        crc = makeCrc(buffer)
        buffer.putShort(crc.toShort())
        
        return buffer
    }

    /**
     * Deserialize a byte buffer into the structure values.
     * 
     * This version of unpack determines the structure version to be used for
     * unpacking by fetching the version info at the beginning of the buffer.
     * 
     * @return false on CRC error, true on successful read and successful CRC check
     */
    fun unpack(val buffer: ByteBuffer) : Boolean {

        val version = getVersion(buffer)
        val fields = getFields(version)

        return unpack(buffer, fields)
    }

    /**
     * Deserialize a byte buffer into the structure values.
     * 
     * @return false on CRC error, true on successful read and successful CRC check
     */
    fun unpack(val buffer: ByteBuffer, val fields: List<DataField>) : Boolean {
        unpackFields(buffer, fields)

        // get slice of buffer over which the CRC should be calculated (from position 0 to 
        // current position)
        val dataBuffer = buffer.duplicate().apply {
            flip()
        }
        val crcCalculated = calculateCrc16(dataBuffer)

        // read recorded CRC
        val crcRecorded = unpackCrc(buffer)

        return (crcRecorded == crcCalculated)
    }

    /**
     * Detect structure version by reading the first byte of the binary values.
     * 
     * The buffer position will not advance.
     * 
     * @param buffer the ByteBuffer containing the version byte at the current 
     *   buffer position
     */
    fun getVersion(val buffer: ByteBuffer) : Int {
        val position = buffer.position()
        val version: Int = buffer.getByte().toInt()
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
     * Write the value of this data field to an existing ByteBuffer, modifying it.
     */
    fun writeTo(buffer: ByteBuffer)

    /**
     * Retrieve the value of this data field by consuming the appropriate number
     * of bytes from the ByteBuffer and converting them into the data field value.
     */
    fun getFrom(buffer: ByteBuffer)
}

/**
 * Class representing a UInt8 data field.
 */
class UInt8 : DataField {
    var value: UByte

    override fun writeTo(buffer: ByteBuffer) {
        buffer.putByte(value.toByte())
    }

    override fun getFrom(buffer: ByteBuffer) {
        value = buffer.getByte().toUByte()
    }
}

/**
 * Class representing a UInt16 data field.
 */
class UInt16 : DataField {
    var value: UShort

    override fun writeTo(buffer: ByteBuffer) {
        buffer.putShort(value.toShort())
    }

    override fun getFrom(buffer: ByteBuffer) {
        value = buffer.getShort().toUShort()
    }
}

/**
 * Class representing a UInt32 data field.
 */
class UInt32 : DataField {
    var value: UInt

    override fun writeTo(buffer: ByteBuffer) {
        buffer.putInt(value.toInt())
    }

    override fun getFrom(buffer: ByteBuffer) {
        value = buffer.getInt().toUInt()
    }
}

/**
 * Class representing a UInt64 data field.
 */
class UInt64 : DataField {
    var value: ULong

    override fun writeTo(buffer: ByteBuffer) {
        buffer.putLong(value.toLong())
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
 class NullBytes(val size: Int) : DataField {

    override fun writeTo(buffer: ByteBuffer) {
        val arr = ByteArray(size) { i -> 0x00 }
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
data class ConfigPof (

    // FIXME Nulls erlauben? Falls zB eine Version der ConfigStruct bestimmte Felder
    // noch nicht kennt, sollten die ja null sein, oder? Damit das UI das 
    // visualisieren kann. Oder gibt es immer DefaultValues (so wie hier aktuell
    // implementiert)?
    var pofStructVersion: UInt8 = 1,
    // val hwType: UByte,
    // val hwRevision: UByte,
    var hwNumber: UInt16 = 0
    var cpuSerial: UInt32 = 0,
    var crc: UInt16 = 0,


    // note that the trailing CRC16 will be added implicitly and must NOT be
    // included in the field list
    val configPofV1 = listOf<DataField>(
        NullBytes(1),
        pofStructVersion,
        NullBytes(2),
        hwNumber,
        cpuSerial,
    ) 

    // note that the trailing CRC16 will be added implicitly and must NOT be
    // included in the field list
    val configPofV2 = listOf<DataField>(
        pofStructVersion,
        cpuSerial,
        hwNumber,
        NullBytes(3),
    ) 
) : ConfigStructure {

    /**
     * For the time being, hardcode the configPof version to configPofV1
     */
    fun unpack(val buffer: ByteBuffer) : Boolean {
        return super.unpack(buffer, configPofV1)
    }

    /**
     * For the time being, hardcode the configPof version to configPofV1
     */
    fun pack(val fields: List<DataField>): ByteBuffer {
        return super.pack(buffer, configPofV1)
    }
}


/**
 * Example for decoding a byte stream into a config object.
 */
fun decodeConfig() {
    val byteArray: ByteArray = ubyteArrayOf(0x00U, 0xA1U, 0x00U, 0x00U, 0x2EU, 0x38U, 0xD4U, 0x89U, 0xC8U, 0xA3U, 0x2EU, 0x08U, 0xF4U, 0x79U).toByteArray()


    configPof = ConfigPof().apply {
        unpack(byteArray)
    }


    val buffer = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN)

    // determine CRC16
    val crc16 = CRC16()
    crc16.update(byteArray)



    // decode buffer

    // skip reserved field
    skip(buffer, 1)
    // buffer.get()

    val pofStructVersion = buffer.get().toUByte()

    // skip reserved field
    skip(buffer, 2)

    val hwNumber = buffer.getShort().toUShort()
    val cpuSerial = buffer.getInt().toULong()

    val wsnConfig = configPof(
    	pofStructVersion,
        hwNumber,
        cpuSerial,
        crc=crc16.value
    )
    println(wsnConfig)
}