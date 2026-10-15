package com.prateekmahendrakar.metadatawiper.metadata.gif

/*
 * One block as absolute file offsets, [start] inclusive and [end] exclusive, covering its
 * sub-blocks and their terminator. The Header block is the signature, logical screen
 * descriptor and global color table together. [applicationId] is the 8-byte identifier plus
 * 3-byte authentication code of an application extension, and empty for every other kind.
 */
data class GifBlock(val kind: GifBlockKind,
                    val start: Long,
                    val end: Long,
                    val applicationId: String = "")
