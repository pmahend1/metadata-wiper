package com.prateekmahendrakar.metadatawiper.metadata.jpeg

import com.prateekmahendrakar.metadatawiper.metadata.ByteArrayChannel
import com.prateekmahendrakar.metadatawiper.metadata.io.BinaryReader

object JpegTestData {

    // region State

    val fixtureNames =
        listOf("jpeg-camera.jpg", "jpeg-clean.jpg", "jpeg-cmyk.jpg", "jpeg-motion-photo.jpg", "jpeg-progressive.jpg")

    // endregion State

    // region Methods

    fun fixture(name: String): ByteArray = javaClass.getResourceAsStream("/fixtures/$name")!!.use { it.readBytes() }

    fun segment(marker: Int,
                payload: ByteArray): ByteArray {
        val length = payload.size + 2
        return bytes(0xFF, marker, length shr 8, length and 0xFF) + payload
    }

    fun applicationSegment(marker: Int,
                           identifier: String,
                           body: ByteArray = ByteArray(0)): ByteArray {
        return segment(marker, identifier.toByteArray(Charsets.ISO_8859_1) + body)
    }

    // Inserts segments straight after SOI, so the base image still decodes.
    fun withSegments(base: ByteArray,
                     vararg segments: ByteArray): ByteArray {
        return base.copyOfRange(0, 2) + segments.fold(ByteArray(0)) { all, next -> all + next } + base.copyOfRange(2,
                                                                                                                   base.size)
    }

    fun segments(jpeg: ByteArray): List<JpegSegment> = JpegSegmentReader(BinaryReader(ByteArrayChannel(jpeg))).read()

    fun bytesOf(jpeg: ByteArray,
                segment: JpegSegment): ByteArray = jpeg.copyOfRange(segment.start.toInt(), segment.end.toInt())

    fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    // endregion Methods
}
