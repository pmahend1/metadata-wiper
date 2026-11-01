package com.prateekmahendrakar.metadatawiper.wipe

import java.io.IOException

class InsufficientSpaceException(val bytesNeeded: Long) : IOException("Not enough free space")
