package com.prateekmahendrakar.metadatawiper.metadata.movie

import com.prateekmahendrakar.metadatawiper.metadata.io.FileSpan
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.Box

/*
 * One `traf`, resolved to where its samples are: a span per `trun`. [followsPreviousFragment] is
 * set when its offsets count from the end of the previous `traf`'s data, so that one can't be
 * freed without moving this one's samples for any reader.
 */
class TrackFragment(val box: Box,
                    val trackId: Long,
                    val runs: List<FileSpan>,
                    val followsPreviousFragment: Boolean)
