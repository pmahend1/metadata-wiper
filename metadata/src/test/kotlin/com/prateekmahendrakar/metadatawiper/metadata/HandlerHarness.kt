package com.prateekmahendrakar.metadatawiper.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import java.io.ByteArrayOutputStream

// Runs a handler over in-memory bytes and checks the parts of the handler contract every format shares.
class HandlerHarness(private val handler: FormatHandler) {

    // region State

    private val keptCategories = setOf(MetadataCategory.ColorProfile, MetadataCategory.Orientation)

    // endregion State

    // region Methods

    fun fixture(name: String): ByteArray = javaClass.getResourceAsStream("/fixtures/$name")!!.use { it.readBytes() }

    fun inspect(bytes: ByteArray): InspectionResult = handler.inspect(ByteArrayChannel(bytes))

    fun strip(bytes: ByteArray,
              options: StripOptions = StripOptions()): Pair<ByteArray, StripReport> {
        val output = ByteArrayOutputStream()
        val report = handler.strip(ByteArrayChannel(bytes), output, options)
        return output.toByteArray() to report
    }

    fun assertOnlyKeptCategoriesRemain(name: String,
                                       output: ByteArray) {
        val remaining = inspect(output).items.filter { it.category !in keptCategories }
        assertEquals(name, emptyList<MetadataItem>(), remaining)
    }

    // Every cut before [imageEnd] loses part of the image, so it must fail cleanly and write nothing.
    fun assertRejectsEveryTruncation(name: String,
                                     input: ByteArray,
                                     imageEnd: Int) {
        for (length in 0 until imageEnd) {
            val truncated = input.copyOf(length)
            assertThrows("$name cut at $length", MalformedFileException::class.java) { inspect(truncated) }
            val output = ByteArrayOutputStream()
            assertThrows("$name cut at $length", MalformedFileException::class.java) {
                handler.strip(ByteArrayChannel(truncated), output, StripOptions())
            }
            assertEquals("$name cut at $length", 0, output.size())
        }
    }

    fun assertHas(items: List<MetadataItem>,
                  category: MetadataCategory,
                  label: String,
                  detail: String = "") {
        assertTrue("Missing $label=$detail in $items",
                   items.any { it.category == category && it.label == label && it.detail == detail })
    }

    // endregion Methods
}
