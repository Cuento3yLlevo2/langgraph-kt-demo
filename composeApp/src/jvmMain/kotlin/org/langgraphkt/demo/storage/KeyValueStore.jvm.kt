package org.langgraphkt.demo.storage

import java.io.File

actual fun platformStore(): KeyValueStore = FileStore(File(System.getProperty("user.home"), ".langgraph-kt-demo"))
