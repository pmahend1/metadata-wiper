package com.prateekmahendrakar.metadatawiper.metadata

class FormatHandlerRegistry(handlers: List<FormatHandler>) {

    // region State

    private val handlersByFormat = handlers.associateBy { it.format }

    // endregion State

    init {
        require(handlersByFormat.size == handlers.size) { "Each format can have only one handler" }
    }

    // region Methods

    fun handlerFor(format: FileFormat): FormatHandler? = handlersByFormat[format]

    // endregion Methods
}
