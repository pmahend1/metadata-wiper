package com.prateekmahendrakar.metadatawiper.verify

enum class DecodeCheck {
    Passed,
    Failed,

    // The platform can't decode this format (or the original), so nothing was proven either way.
    Skipped
}
