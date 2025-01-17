/*
 * Copyright 2023 pyamsoft
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pyamsoft.tetherfi.server.proxy

import androidx.annotation.CheckResult
import com.pyamsoft.pydroid.bus.EventBus
import com.pyamsoft.pydroid.core.ThreadEnforcer
import com.pyamsoft.pydroid.util.ifNotCancellation
import com.pyamsoft.tetherfi.core.Timber
import com.pyamsoft.tetherfi.server.BaseServer
import com.pyamsoft.tetherfi.server.ServerInternalApi
import com.pyamsoft.tetherfi.server.clients.ClientEraser
import com.pyamsoft.tetherfi.server.clients.StartedClients
import com.pyamsoft.tetherfi.server.event.ServerShutdownEvent
import com.pyamsoft.tetherfi.server.proxy.manager.ProxyManager
import com.pyamsoft.tetherfi.server.status.RunningStatus
import com.pyamsoft.tetherfi.server.widi.WiDiNetworkStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
internal class WifiSharedProxy
@Inject
internal constructor(
    @ServerInternalApi private val factory: ProxyManager.Factory,
    private val enforcer: ThreadEnforcer,
    private val clientEraser: ClientEraser,
    private val startedClients: StartedClients,
    private val shutdownBus: EventBus<ServerShutdownEvent>,
    status: ProxyStatus,
) : BaseServer(status), SharedProxy {

  private val overallState =
      MutableStateFlow(
          ProxyState(
              tcp = false,
              udp = false,
          ),
      )

  private fun readyState(type: SharedProxy.Type) {
    overallState.update { s ->
      when (type) {
        SharedProxy.Type.TCP -> s.copy(tcp = true)
        SharedProxy.Type.UDP -> s.copy(udp = true)
      }
    }
  }

  private fun resetState() {
    overallState.update {
      it.copy(
          tcp = false,
          udp = false,
      )
    }
  }

  private suspend fun handleServerLoopError(e: Throwable, type: SharedProxy.Type) {
    e.ifNotCancellation {
      Timber.e(e) { "Error running server loop: ${type.name}" }

      reset()
      status.set(RunningStatus.ProxyError(e.message ?: "An unexpected error occurred."))
      shutdownBus.emit(ServerShutdownEvent)
    }
  }

  private suspend fun beginProxyLoop(
      type: SharedProxy.Type,
      info: WiDiNetworkStatus.ConnectionInfo.Connected,
  ) {
    enforcer.assertOffMainThread()

    try {
      Timber.d { "${type.name} Begin proxy server loop: $info" }
      factory
          .create(
              type = type,
              info = info,
          )
          .loop { readyState(type) }
    } catch (e: Throwable) {
      handleServerLoopError(e, type)
    }
  }

  private fun CoroutineScope.proxyLoop(info: WiDiNetworkStatus.ConnectionInfo.Connected) {
    launch(context = Dispatchers.Default) {
      beginProxyLoop(
          type = SharedProxy.Type.TCP,
          info = info,
      )
    }

    // TODO: UDP support
    if (FLAG_ENABLE_UDP) {
      launch(context = Dispatchers.Default) {
        beginProxyLoop(
            type = SharedProxy.Type.UDP,
            info = info,
        )
      }
    }
  }

  private fun reset() {
    enforcer.assertOffMainThread()

    clientEraser.clear()
    resetState()
  }

  private suspend fun shutdown() =
      withContext(context = NonCancellable) {
        enforcer.assertOffMainThread()

        // Update status if we were running
        if (status.get() is RunningStatus.Running) {
          status.set(RunningStatus.Stopping)
        }

        reset()
        status.set(RunningStatus.NotRunning)
      }

  private fun CoroutineScope.watchServerReadyStatus() {
    // When all proxy bits declare they are ready, the proxy status is "ready"
    overallState
        .map { it.isReady() }
        .filter { it }
        .also { f ->
          launch(context = Dispatchers.Default) {
            f.collect { ready ->
              if (ready) {
                Timber.d { "Proxy has fully launched, update status!" }
                status.set(
                    RunningStatus.Running,
                    clearError = true,
                )
              }
            }
          }
        }
  }

  private suspend fun startServer(info: WiDiNetworkStatus.ConnectionInfo.Connected) {
    try {
      // Launch a new scope so this function won't proceed to finally block until the scope is
      // completed/cancelled
      //
      // This will suspend until the proxy server loop dies
      coroutineScope {
        // Mark proxy launching
        Timber.d { "Starting proxy server ..." }
        status.set(
            RunningStatus.Starting,
            clearError = true,
        )

        watchServerReadyStatus()

        // Notify the client connection watcher that we have started
        launch(context = Dispatchers.Default) { startedClients.started() }

        // Start the proxy server loop
        launch(context = Dispatchers.Default) { proxyLoop(info) }
      }
    } finally {
      Timber.d { "Stopped Proxy Server" }
    }
  }

  private suspend fun Job.stopProxyLoop() {
    status.set(RunningStatus.Stopping)
    cancelAndJoin()
  }

  override suspend fun start(connectionStatus: Flow<WiDiNetworkStatus.ConnectionInfo>) =
      withContext(context = Dispatchers.Default) {
        // Scope local
        val mutex = Mutex()
        var job: Job? = null

        // Watch the connection status
        try {
          // Launch a new scope so this function won't proceed to finally block until the scope is
          // completed/cancelled
          //
          // This will suspend until the proxy server loop dies
          coroutineScope {

            // Watch the connection status for valid info
            connectionStatus.distinctUntilChanged().collect { info ->
              when (info) {
                is WiDiNetworkStatus.ConnectionInfo.Connected -> {
                  // Connected is good, we can launch
                  // This will re-launch any time the connection info changes
                  mutex.withLock {
                    job?.stopProxyLoop()
                    job = null

                    reset()

                    // Hold onto the job here so we can cancel it if we need to
                    job = launch(context = Dispatchers.Default) { startServer(info) }
                  }
                }
                is WiDiNetworkStatus.ConnectionInfo.Empty -> {
                  Timber.w { "Connection EMPTY, shut down Proxy" }

                  // Empty is missing the channel, bad
                  mutex.withLock {
                    job?.stopProxyLoop()
                    job = null
                  }
                  shutdown()
                }
                is WiDiNetworkStatus.ConnectionInfo.Error -> {
                  Timber.w { "Connection ERROR, shut down Proxy" }

                  // Error is bad, shut down the proxy
                  mutex.withLock {
                    job?.stopProxyLoop()
                    job = null
                  }
                  shutdown()
                }
                is WiDiNetworkStatus.ConnectionInfo.Unchanged -> {
                  Timber.w { "UNCHANGED SHOULD NOT HAPPEN" }
                  // This should not happen - coding issue
                  mutex.withLock {
                    job?.stopProxyLoop()
                    job = null
                  }
                  shutdown()
                  throw AssertionError(
                      "GroupInfo.Unchanged should never escape the server-module internals.",
                  )
                }
              }
            }
          }
        } finally {
          withContext(context = NonCancellable) {
            Timber.d { "Shutting down proxy..." }
            mutex.withLock {
              job?.stopProxyLoop()
              job = null
            }

            shutdown()

            Timber.d { "Proxy Server is Done!" }
          }
        }
      }

  private data class ProxyState(
      val tcp: Boolean,
      val udp: Boolean,
  ) {

    @CheckResult
    fun isReady(): Boolean {
      if (!tcp) {
        return false
      }

      return if (FLAG_ENABLE_UDP) udp else true
    }
  }

  companion object {
    private const val FLAG_ENABLE_UDP = false
  }
}
