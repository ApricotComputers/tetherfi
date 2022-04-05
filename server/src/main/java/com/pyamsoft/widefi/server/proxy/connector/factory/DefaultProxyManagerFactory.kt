package com.pyamsoft.widefi.server.proxy.connector.factory

import androidx.annotation.CheckResult
import com.pyamsoft.widefi.server.ServerInternalApi
import com.pyamsoft.widefi.server.proxy.SharedProxy
import com.pyamsoft.widefi.server.proxy.connector.ProxyManager
import com.pyamsoft.widefi.server.proxy.connector.TcpProxyManager
import com.pyamsoft.widefi.server.proxy.connector.UdpProxyManager
import com.pyamsoft.widefi.server.proxy.session.ProxySession
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.Socket
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

@Singleton
internal class DefaultProxyManagerFactory
@Inject
internal constructor(
    @ServerInternalApi private val proxyDebug: Boolean,
    @ServerInternalApi private val dispatcher: CoroutineDispatcher,
    @ServerInternalApi private val tcpSessionFactory: ProxySession.Factory<Socket>,
    @ServerInternalApi private val udpSessionFactory: ProxySession.Factory<Datagram>,
) : ProxyManager.Factory {

  @CheckResult
  private fun createTcp(port: Int): ProxyManager {
    return TcpProxyManager(
        port = port,
        dispatcher = dispatcher,
        proxyDebug = proxyDebug,
        factory = tcpSessionFactory,
    )
  }

  @CheckResult
  private fun createUdp(port: Int): ProxyManager {
    return UdpProxyManager(
        port = port,
        dispatcher = dispatcher,
        proxyDebug = proxyDebug,
        factory = udpSessionFactory,
    )
  }

  override fun create(type: SharedProxy.Type, port: Int): ProxyManager {
    return when (type) {
      SharedProxy.Type.TCP -> createTcp(port = port)
      SharedProxy.Type.UDP -> createUdp(port = port)
    }
  }
}