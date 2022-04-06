package com.siemens.bmd.configapp

import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
 FIXME since "structure version" Byte at the beginning of the struct is mandatory, should we exclude
 it from being modifiable? I.e., remove it from the "field list" and treat it like the CRC field
 implicitly? Otherwise it might be possible that a use misconfigures the field list, so that the
 version field is not the first byte - which would lead to strange effects
 */

/**
 * Base class for config structures.
 *
 * Includes packing/unpacking of fields. The last bytes of the data structure is
 * always assumed to be a CRC16 checksum. This will be checked/written automatically.
 *
 * Basic assumptions about config structures:
 * * the first byte (UInt8) determines the structure version to be used for
 *   packing/unpacking
 * * the last UInt16 is the CRC16 checksum
 * * all data fields must be provided by the respective subclass
 */
abstract class ConfigStructure(var version: Int = 1) {
    private val sizeOfCrc16 = UShort.SIZE_BYTES

    /**
     * Detect structure version by reading the first byte of the binary values.
     *
     * Can be called from "outside" to first nail down the config structure (and the
     * number of bytes which must be read) and then read the remaining bytes, then
     * let this class decode the entire content.
     *
     * @param byteArray the binary data containing the version byte at the current
     *   buffer position
     */
    fun getVersion(byteArray: ByteArray): Int {
        return byteArray[0].toInt()
    }

    /**
     * Determine the size of the config structure in the given version.
     *
     * @param version the structure version
     * @return the number of bytes needed for the structure in the specified version
     */
    fun getSize(version: Int): Int {

        val dataFields = getDataFields(version) ?: throw Exception(
            "unknown version of $version, " + "cannot determine size of structure version"
        )

        // to the aggregated size of the fields, add the bytes needed for the CRC16
        return dataFields.sumOf { it.size } + sizeOfCrc16
    }

    /**
     * Given the structure version, determine the appropriate field list.
     *
     * @param version the structure version
     * @return the structure "layout", i.e. the list of data fields, ready
     *   to be used for packing or unpacking - or null if the specified version is unknown
     */
    abstract fun getDataFields(version: Int): List<DataField>?


    /**
     * Serialize the config structure.
     *
     * The structure version used is taken from this class' "version" property. This is
     * set on unpacking or explicitly (thereafter). So, by default, packing will use the
     * same version as the former unpacking, so the config can be manipulated and written
     * back using the same version.
     *
     * However, if you would like to "upgrade" the config structure, it is possible to
     * explicitly set the version to a different value (after unpacking), so the new
     * version is used for packing (writing).
     *
     * @return the serialized structure, including the trailing CRC16 checksum
     */
    fun pack(): ByteArray {

        println("for packing, use version $version")
        val fields: List<DataField>? = getDataFields(version)
        if (fields == null) {
            println("unable to pack structure because structure version $version is unknown")
            throw Exception("unable to pack structure because structure version $version is unknown")  // FIXME wie soll ich mit einem solchen Fehler umgehen? Was soll passieren?
        }

        return pack(fields).apply {
            // ensure that the version byte (first byte) reflects the actual version written
            set(0, version.toByte())
        }
    }


    /**
     * Serialize the config structure.
     *
     * @return the serialized structure, including the trailing CRC16 checksum
     */
    private fun pack(fields: List<DataField>): ByteArray {
        val buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN)

        packFields(buffer, fields)

        // add CRC checksum at the end of the buffer
        val crc = calculateCrc16(buffer)
        writeCrc16(buffer, crc)

        // return the ByteArray version of the data
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
    fun unpack(byteArray: ByteArray) {

        val version = getVersion(byteArray)
        println("from the data, determined version=$version")
        //val fields = getFields(version)

        val fields: List<DataField>? = getDataFields(version)
        if (fields == null) {
            println("unable to unpack structure because structure version $version is unknown")
            return // FIXME wie soll ich mit einem solchen Fehler umgehen? Was soll passieren?
        }

        unpack(byteArray, fields)

        // remember the read version and set it as default for a later packing
        // (i.e., by default, leave the version "as is")
        this.version = version
    }

    /**
     * Deserialize a byte buffer into the structure values.
     *
     * @throws Exception on CRC error
     */
    private fun unpack(byteArray: ByteArray, fields: List<DataField>) {

        val buffer = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN)

        unpackFields(buffer, fields)

        // get slice of buffer over which the CRC should be calculated (from position 0 to
        // current position)
        val dataBuffer = buffer.duplicate().apply {
            flip()
        }
        val crcCalculated = calculateCrc16(dataBuffer)
        println("calculated CRC=${crcCalculated}")

        // read recorded CRC
        val crcRecorded = readCrc16(buffer)
        println("recorded CRC=${crcRecorded}")

        if (crcRecorded != crcCalculated) {
            throw Exception("checksum mismatch!")
        }
    }

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
    private fun packFields(buffer: ByteBuffer, fields: List<DataField>) {
        fields.forEach {
            it.writeTo(buffer)
        }
    }


    /**
     * Unpack the given fields (in the given order and layout) from the
     * passed ByteBuffer. The CRC will not be extracted and is expected
     * to be the next bytes in the buffer.
     *
     * @param buffer the byte buffer to be read from
     * @param fields the data field layout for parsing the fields
     */
    private fun unpackFields(buffer: ByteBuffer, fields: List<DataField>) {
        fields.forEach {
            it.getFrom(buffer)
        }
    }

    /**
     * Read the next bytes and interpret them as CRC value. Unpack the value
     * and return it.
     *
     * @param buffer the byte buffer to be read from
     * @return the CRC16 value read from the byte buffer
     */
    private fun readCrc16(buffer: ByteBuffer): UShort {
        return buffer.short.toUShort()
    }


    /**
     * Write the CRC number to the buffer.
     *
     * @param buffer the byte buffer to be written to (on the current position)
     * @param crc the CRC16 value to be written to the byte buffer
     */
    private fun writeCrc16(buffer: ByteBuffer, crc: UShort) {
        buffer.putShort(crc.toShort())
    }


    /**
     * Calculate CRC16 over the given ByteBuffer content. The buffer will be
     * unchanged, since this function operates on a duplicate.
     *
     * @param buffer the ByteBuffer over which the CRC16 will be calculated
     * @return the calculated CRC16 value
     */
    private fun calculateCrc16(buffer: ByteBuffer): UShort {
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
}

/**
 * The interface for all data fields of a config structure.
 *
 * This interface enables the fields to be serialized/deserialized
 * to/from a given ByteBuffer.
 */
interface DataField {

    /**
     * The size of the data field in bytes.
     */
    val size: Int

    /**
     * The name of this data field, used for output/logging.
     */
    val name: String

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
class UInt8(override val name: String, override val size: Int = 1) : DataField {
    var value: UByte? = null

    override fun writeTo(buffer: ByteBuffer) {
        if (value == null) {
            throw Exception("no value for field $name defined, cannot serialize it")
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
class UInt16(override val name: String, override val size: Int = 2) : DataField {
    var value: UShort? = null

    override fun writeTo(buffer: ByteBuffer) {
        if (value == null) {
            throw Exception("no value for field $name defined, cannot serialize it")
        }
        value?.let { buffer.putShort(it.toShort()) }
    }

    override fun getFrom(buffer: ByteBuffer) {
        value = buffer.short.toUShort()
    }
}

/**
 * Class representing a UInt32 data field.
 */
class UInt32(override val name: String, override val size: Int = 4) : DataField {
    var value: UInt? = null

    override fun writeTo(buffer: ByteBuffer) {
        if (value == null) {
            throw Exception("no value for field $name defined, cannot serialize it")
        }
        value?.let { buffer.putInt(it.toInt()) }
    }

    override fun getFrom(buffer: ByteBuffer) {
        value = buffer.int.toUInt()
    }
}

/**
 * Class representing a UInt64 data field.
 */
class UInt64(override val name: String, override val size: Int = 8) : DataField {
    var value: ULong? = null

    override fun writeTo(buffer: ByteBuffer) {
        if (value == null) {
            throw Exception("no value for field $name defined, cannot serialize it")
        }
        value?.let { buffer.putLong(it.toLong()) }
    }

    override fun getFrom(buffer: ByteBuffer) {
        value = buffer.long.toULong()
    }
}

/**
 * A consecutive number of Null bytes, a.k.a. "reserved field"
 *
 * On writing, it renders to 0x00 bytes. On reading, the actual
 * read bytes will be dismissed (ignored), except for contributing
 * to the checksum.
 */
class NullBytes(override val name: String, override val size: Int) : DataField {

    override fun writeTo(buffer: ByteBuffer) {
        val arr = ByteArray(size) { 0x00 }
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
class ConfigPof(

    // FIXME Nulls erlauben? Falls zB eine Version der ConfigStruct bestimmte Felder
    // noch nicht kennt, sollten die ja null sein, oder? Damit das UI das
    // visualisieren kann. Oder gibt es immer DefaultValues (so wie hier aktuell
    // implementiert)?
    val pofStructVersion: UInt8 = UInt8("pofStructVersion"),
    val hwNumber: UInt16 = UInt16("hwNumber"),
    val cpuSerial: UInt32 = UInt32("cpuSerial"),
    val crc: UInt16 = UInt16("crc"),


    // note that the trailing CRC16 will be added implicitly and must NOT be
    // included in the field list
    private val configPofV1: List<DataField> = listOf(
        pofStructVersion,
        NullBytes("reserved1", 1),
        hwNumber,
        NullBytes("reserved2", 2),
        cpuSerial,
    ),

    // note that the trailing CRC16 will be added implicitly and must NOT be
    // included in the field list
    private val configPofV2: List<DataField> = listOf(
        pofStructVersion,
        cpuSerial,
        hwNumber,
        NullBytes("reserved", 3),
    ),

    ) : ConfigStructure() {

    override fun toString(): String {
        return "pofStructVersion=${pofStructVersion.value}, hwNumber=${hwNumber.value}, cpuSerial=${cpuSerial.value}"
    }

    override fun getDataFields(version: Int): List<DataField>? {
        return when (version) {
            1 -> configPofV1
            2 -> configPofV2
            else -> null
        }
    }
}


/**
 * Example for decoding a byte stream into a config object.
 */
@OptIn(ExperimentalUnsignedTypes::class)
fun decodeConfig() {
    val byteArray: ByteArray = ubyteArrayOf(
        0x01U, 0x00U, 0xA1U, 0xccU, 0x00U, 0x00U, 0x2EU, 0x38U, 0xD4U, 0x89U, 0x11U, 0x22U
    ).toByteArray()

    printByteArray("initial data: : ", byteArray)

    val configPof = ConfigPof()

    // determine the struct version according to the data bytes (according to the FIRST byte of the data)
    val version = configPof.getVersion(byteArray)

    val size = configPof.getSize(version)
    println("determined size of configPof in version [$version] is: $size bytes")

    // deserialize: read from bytes into the config values
    configPof.unpack(byteArray)

    // manipulate data
    configPof.hwNumber.value = 0x0103U

    // write using another structure version
    configPof.version = 2

    // serialize: read from the config values into the serialized bytes
    val byteArray2 = configPof.pack()

    printByteArray("bytearray of newly packed configPof is: ", byteArray2)

    println(configPof)
//    print("configPof: hwNumber=%04x".format(configPof.hwNumber.value.toInt()))
    print("configPof: hwNumber=%04x".format(65520))

    println("cpuSerial is: ${configPof.cpuSerial.value}")
}

/**
 * Helper function which prints the bytes of a byte array on stdout.
 */
fun printByteArray(comment: String, byteArray: ByteArray) {
    println(comment)
    byteArray.forEach {
        val b = it.toUByte()
        print("  $b")
    }
    println()
}

fun main() {
    decodeConfig()
}