package com.prateekmahendrakar.metadatawiper.metadata.jpeg

// Marker codes: the byte that follows 0xFF.
object JpegMarker {

    // region State

    const val PREFIX = 0xFF
    const val STUFFED_ZERO = 0x00

    const val SOF0 = 0xC0
    const val DHT = 0xC4
    const val JPG = 0xC8
    const val DAC = 0xCC
    const val SOF15 = 0xCF
    const val RST0 = 0xD0
    const val RST7 = 0xD7
    const val SOI = 0xD8
    const val EOI = 0xD9
    const val SOS = 0xDA
    const val DQT = 0xDB
    const val DNL = 0xDC
    const val DRI = 0xDD
    const val APP0 = 0xE0
    const val APP1 = 0xE1
    const val APP2 = 0xE2
    const val APP13 = 0xED
    const val APP14 = 0xEE
    const val APP15 = 0xEF
    const val COM = 0xFE

    // endregion State

    // region Methods

    // DHT, JPG and DAC share the SOFn code range but aren't frame headers.
    fun isStartOfFrame(marker: Int): Boolean = marker in SOF0..SOF15 && marker != DHT && marker != JPG && marker != DAC

    fun isRestart(marker: Int): Boolean = marker in RST0..RST7

    fun isApplication(marker: Int): Boolean = marker in APP0..APP15

    fun isTable(marker: Int): Boolean = marker == DHT || marker == DAC || marker == DQT || marker == DRI || marker == DNL

    // endregion Methods
}
