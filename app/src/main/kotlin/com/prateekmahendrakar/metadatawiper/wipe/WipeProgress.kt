package com.prateekmahendrakar.metadatawiper.wipe

// A null fraction means the phase can't measure how far along it is.
data class WipeProgress(val fileIndex: Int,
                        val fileCount: Int,
                        val fileName: String,
                        val phase: WipePhase,
                        val fraction: Float?,
                        val canCancel: Boolean)
