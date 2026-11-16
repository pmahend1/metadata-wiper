package com.prateekmahendrakar.metadatawiper.metadata.movie

import com.prateekmahendrakar.metadatawiper.metadata.nal.VideoCodec

// How a video track's samples are coded: the codec, and how many bytes give each NAL unit's length.
data class VideoStreamFormat(val codec: VideoCodec,
                             val lengthSize: Int)
