package com.prateekmahendrakar.metadatawiper

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

// Copies of the :metadata video fixtures. Each test gets its own file, so one test's overwrite can't reach another's.
object VideoFixtures {

    // region Methods

    fun copyToCache(name: String): File {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File.createTempFile("fixture", name, instrumentation.targetContext.cacheDir)
        instrumentation.context.assets.open("fixtures/$name").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return file
    }

    // endregion Methods
}
