package com.pyamsoft.tetherfi.server.proxy

import android.content.Context
import android.os.PowerManager
import androidx.annotation.CheckResult
import androidx.core.content.getSystemService
import com.pyamsoft.pydroid.bus.EventBus
import com.pyamsoft.pydroid.core.requireNotNull
import com.pyamsoft.tetherfi.core.generateRandomId
import com.pyamsoft.tetherfi.server.BaseServer
import com.pyamsoft.tetherfi.server.ServerInternalApi
import com.pyamsoft.tetherfi.server.ServerPreferences
import com.pyamsoft.tetherfi.server.event.ConnectionEvent
import com.pyamsoft.tetherfi.server.event.ErrorEvent
import com.pyamsoft.tetherfi.server.logging.LogStorage
import com.pyamsoft.tetherfi.server.proxy.connector.ProxyManager
import com.pyamsoft.tetherfi.server.status.RunningStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

@Singleton
internal class WifiSharedProxy
@Inject
internal constructor(
    private val context: Context,
    private val preferences: ServerPreferences,
    @ServerInternalApi private val activityLogStorage: LogStorage<ConnectionEvent>,
    @ServerInternalApi private val errorLogStorage: LogStorage<ErrorEvent>,
    @ServerInternalApi private val dispatcher: CoroutineDispatcher,
    @ServerInternalApi private val errorBus: EventBus<ErrorEvent>,
    @ServerInternalApi private val connectionBus: EventBus<ConnectionEvent>,
    @ServerInternalApi private val factory: ProxyManager.Factory,
    status: ProxyStatus,
) : BaseServer(status), SharedProxy {

  private val powerManager by lazy { context.getSystemService<PowerManager>().requireNotNull() }
  private var wakeLock: PowerManager.WakeLock? = null

  private val mutex = Mutex()
  private val jobs = mutableListOf<ProxyJob>()

  /** We own our own scope here because the proxy lifespan is separate */
  private val scope = CoroutineScope(context = dispatcher)

  init {
    scope.launch(context = dispatcher) { connectionBus.onEvent { activityLogStorage.submit(it) } }

    scope.launch(context = dispatcher) { errorBus.onEvent { errorLogStorage.submit(it) } }
  }

  /** Get the port for the proxy */
  @CheckResult
  private suspend fun getPort(): Int =
      withContext(context = dispatcher) {
        return@withContext preferences.getPort()
      }

  @CheckResult
  private fun CoroutineScope.proxyLoop(type: SharedProxy.Type, port: Int): ProxyJob {
    val tcp = factory.create(type = type, port = port)

    Timber.d("${type.name} Begin proxy server loop")
    val job = launch(context = dispatcher) { tcp.loop() }
    return ProxyJob(type = type, job = job)
  }

  private fun releaseWakelock() {
    val w = wakeLock
    if (w != null) {
      Timber.d("Releasing partial CPU wake lock $WAKE_LOCK_TAG")
      w.release()
    }
    wakeLock = null
  }

  private suspend fun shutdown() {
    try {
      clearJobs()

      // Clear busses
      errorBus.send(ErrorEvent.Clear(id = generateRandomId()))
      connectionBus.send(ConnectionEvent.Clear(id = generateRandomId()))

      // Clear storage
      activityLogStorage.clear()
      errorLogStorage.clear()
    } finally {
      mutex.withLock { releaseWakelock() }
    }
  }

  private suspend fun clearJobs() {
    Timber.d("Cancelling jobs")
    mutex.withLock { jobs.removeEach { it.job.cancel() } }
  }

  override fun start() {
    scope.launch(context = dispatcher) {
      shutdown()

      try {
        val port = getPort()
        if (port > 65000 || port < 1024) {
          Timber.w("Port is invalid: $port")
          status.set(RunningStatus.Error(message = "Port is invalid: $port"))
          return@launch
        }

        status.set(RunningStatus.Starting)

        coroutineScope {
          val tcp = proxyLoop(type = SharedProxy.Type.TCP, port = port)
          val udp = proxyLoop(type = SharedProxy.Type.UDP, port = port)

          mutex.withLock {
            jobs.add(tcp)
            jobs.add(udp)
          }

          Timber.d("Started proxy server on port: $port")
          status.set(RunningStatus.Running)

          mutex.withLock {
            releaseWakelock()

            Timber.d("Acquire a partial CPU wake lock $WAKE_LOCK_TAG")
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
          }
        }
      } catch (e: Throwable) {
        Timber.e(e, "Error when running the proxy, shut it all down")
        shutdown()
        status.set(RunningStatus.Error(message = e.message ?: "A proxy error occurred"))
      }
    }
  }

  override fun stop() {
    scope.launch(context = dispatcher) {
      Timber.d("Stopping proxy server")

      status.set(RunningStatus.Stopping)

      shutdown()

      status.set(RunningStatus.NotRunning)
    }
  }

  override suspend fun onErrorEvent(block: suspend (ErrorEvent) -> Unit) {
    return errorLogStorage.onLogEvent(block)
  }

  override suspend fun onConnectionEvent(block: suspend (ConnectionEvent) -> Unit) {
    return activityLogStorage.onLogEvent(block)
  }

  private inline fun <T> MutableList<T>.removeEach(block: (T) -> Unit) {
    while (this.isNotEmpty()) {
      block(this.removeFirst())
    }
  }

  private data class ProxyJob(
      val type: SharedProxy.Type,
      val job: Job,
  )

  companion object {
    private const val WAKE_LOCK_TAG = "com.pyamsoft.tetherfi:PROXY_WAKE_LOCK"
  }
}
