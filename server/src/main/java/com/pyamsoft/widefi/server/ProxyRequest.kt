package com.pyamsoft.widefi.server

data class ProxyRequest
internal constructor(
    val version: String,
    val url: String,
    val host: String,
    val method: String,
    val port: Int,
    val raw: String,
)