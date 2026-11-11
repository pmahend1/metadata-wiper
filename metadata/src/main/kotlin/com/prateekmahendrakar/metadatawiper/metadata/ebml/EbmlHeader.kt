package com.prateekmahendrakar.metadatawiper.metadata.ebml

// An element's ID and size as read, before its end is known. [size] is null for the unknown size only Segment and Cluster may use.
data class EbmlHeader(val id: Long,
                      val start: Long,
                      val dataStart: Long,
                      val size: Long?)
