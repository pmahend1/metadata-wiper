package com.prateekmahendrakar.metadatawiper.metadata.movie

import com.prateekmahendrakar.metadatawiper.metadata.isobmff.Box

// One `trex`: the defaults a track's fragments fall back on when they don't state their own.
class TrackExtends(val box: Box,
                   val trackId: Long,
                   val defaultSampleSize: Long)
