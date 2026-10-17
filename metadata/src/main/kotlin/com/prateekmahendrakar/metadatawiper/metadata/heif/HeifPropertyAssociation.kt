package com.prateekmahendrakar.metadatawiper.metadata.heif

// One `ipma` association. The essential flag is the top bit of the byte at [flagOffset], which holds [flagByte].
data class HeifPropertyAssociation(val itemId: Long,
                                   val propertyIndex: Int,
                                   val isEssential: Boolean,
                                   val flagOffset: Long,
                                   val flagByte: Int)
