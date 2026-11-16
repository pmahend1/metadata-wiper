package com.prateekmahendrakar.metadatawiper.metadata.mpeg

// One stream a program map lists. [isKlv] is set by a "KLVA" registration descriptor: SMPTE KLV, which drones use for telemetry.
data class ElementaryStream(val pid: Int,
                            val streamType: Int,
                            val isKlv: Boolean)
