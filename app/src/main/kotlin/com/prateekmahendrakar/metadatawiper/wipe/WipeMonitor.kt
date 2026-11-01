package com.prateekmahendrakar.metadatawiper.wipe

// How a caller follows and stops a wipe. Both are called on the thread doing the wipe.
class WipeMonitor(val isCancelled: () -> Boolean = { false },
                  val onProgress: (WipePhase, Float?) -> Unit = { _, _ -> })
