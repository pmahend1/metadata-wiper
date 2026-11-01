package com.prateekmahendrakar.metadatawiper.verify

// What must survive a strip for a track to still play the same. Fields a track doesn't have are null.
data class VideoTrack(val index: Int,
                      val mimeType: String,
                      val width: Int?,
                      val height: Int?,
                      val sampleRate: Int?,
                      val channelCount: Int?,
                      val durationUs: Long?,
                      val maxSampleSize: Int?) {

    // region State

    val isVideo: Boolean
        get() = mimeType.startsWith("video/")

    // endregion State
}
