package com.prateekmahendrakar.metadatawiper.model

import com.prateekmahendrakar.metadatawiper.metadata.StripOptions

data class PrivacySettings(val keepColorProfile: Boolean = true,
                           val keepOrientation: Boolean = true,
                           val randomizeFileNames: Boolean = false) {

    // region State

    val stripOptions: StripOptions
        get() = StripOptions(keepColorProfile = keepColorProfile, keepOrientation = keepOrientation)

    // endregion State
}
