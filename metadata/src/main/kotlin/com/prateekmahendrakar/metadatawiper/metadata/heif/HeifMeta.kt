package com.prateekmahendrakar.metadatawiper.metadata.heif

import com.prateekmahendrakar.metadatawiper.metadata.isobmff.Box
import com.prateekmahendrakar.metadatawiper.metadata.isobmff.ItemLocation

// The parts of a HEIF `meta` box that locate and describe items.
data class HeifMeta(val children: List<Box>,
                    val items: List<HeifItem>,
                    val locations: Map<Long, ItemLocation>,
                    val references: List<HeifReference>,
                    val properties: List<HeifProperty>,
                    val associations: List<HeifPropertyAssociation>,
                    val idat: Box?)
