package com.pyamsoft.widefi

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.pyamsoft.pydroid.core.requireNotNull
import com.pyamsoft.pydroid.inject.Injector
import com.pyamsoft.pydroid.theme.keylines
import com.pyamsoft.pydroid.ui.app.PYDroidActivity
import com.pyamsoft.pydroid.ui.changelog.buildChangeLog
import com.pyamsoft.widefi.server.ConnectionEvent
import com.pyamsoft.widefi.server.ErrorEvent
import com.pyamsoft.widefi.server.proxy.SharedProxy
import com.pyamsoft.widefi.server.status.RunningStatus
import com.pyamsoft.widefi.server.widi.WiDiNetwork
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : PYDroidActivity() {

  @Inject @JvmField internal var wiDiNetwork: WiDiNetwork? = null

  override val applicationIcon = R.mipmap.ic_launcher_round

  override val changelog = buildChangeLog {}

  private inline fun toggleWiDi(
      on: Boolean,
      crossinline onGroupInfo: (WiDiNetwork.GroupInfo) -> Unit,
      crossinline onConnectionInfo: (WiDiNetwork.ConnectionInfo) -> Unit,
  ) {
    val p = wiDiNetwork.requireNotNull()
    lifecycleScope.launch(context = Dispatchers.Main) {
      if (on) {
        p.stop()
      } else {
        p.start()
      }

      val group = p.getGroupInfo()
      if (group != null) {
        onGroupInfo(group)
      }

      val conn = p.getConnectionInfo()
      if (conn != null) {
        onConnectionInfo(conn)
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    Injector.obtainFromApplication<WidefiComponent>(this).inject(this)

    setContent {
      val proxyErrors = remember { mutableStateListOf<ErrorEvent>() }
      var isWiDiOn by remember { mutableStateOf(false) }

      var ssid by remember { mutableStateOf("") }
      var password by remember { mutableStateOf("") }

      var proxyStatus by remember { mutableStateOf<RunningStatus>(RunningStatus.NotRunning) }
      var widiStatus by remember { mutableStateOf<RunningStatus>(RunningStatus.NotRunning) }

      val connections = remember { mutableStateListOf<String>() }

      LaunchedEffect(true) {
        wiDiNetwork.requireNotNull().also { widi ->
          this.launch(context = Dispatchers.Main) { widi.onStatusChanged { widiStatus = it } }

          this.launch(context = Dispatchers.Main) { widi.onStatusChanged { widiStatus = it } }

          this.launch(context = Dispatchers.Main) { widi.onProxyStatusChanged { proxyStatus = it } }

          this.launch(context = Dispatchers.Main) {
            widi.onErrorEvent { e ->
              when (e) {
                ErrorEvent.Clear -> proxyErrors.clear()
                else -> proxyErrors.add(e)
              }
            }
          }

          this.launch(context = Dispatchers.Main) {
            widi.onConnectionEvent { e ->
              when (e) {
                is ConnectionEvent.Udp -> {}
                is ConnectionEvent.Tcp -> connections.add(e.request.host)
                is ConnectionEvent.Clear -> connections.clear()
              }
            }
          }
        }
      }

      Scaffold {
        Column(
            modifier = Modifier.padding(MaterialTheme.keylines.content),
        ) {
          Button(
              onClick = {
                toggleWiDi(
                    isWiDiOn,
                    onGroupInfo = { info ->
                      ssid = info.ssid
                      password = info.password
                    },
                    onConnectionInfo = { info -> },
                )
                isWiDiOn = !isWiDiOn
              },
          ) {
            Text(
                text = "Turn WideFi: ${if (isWiDiOn) "OFF" else "ON"}",
            )
          }

          Text(
              text = "SSID=$ssid PASSWORD=$password",
              style = MaterialTheme.typography.body1,
          )

          Row(
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
                text = "WiFi Network Status:",
                style = MaterialTheme.typography.body2,
            )
            DisplayStatus(
                status = widiStatus,
            )
          }

          Row(
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
                text = "Proxy Status:",
                style = MaterialTheme.typography.body2,
            )
            DisplayStatus(
                status = proxyStatus,
            )
          }

          LazyColumn(
              verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            item {
              Text(
                  text = "Connections",
                  style =
                      MaterialTheme.typography.body1.copy(
                          fontWeight = FontWeight.Bold,
                      ),
              )
            }

            itemsIndexed(
                items = connections,
                key = { i, c -> "$c-$i" },
            ) { _, conn ->
              Text(
                  text = conn,
                  style = MaterialTheme.typography.body2,
              )
            }

            item {
              Text(
                  text = "Errors",
                  style =
                      MaterialTheme.typography.body1.copy(
                          fontWeight = FontWeight.Bold,
                      ),
              )
            }

            itemsIndexed(
                items = proxyErrors,
                key = { i, e -> "$e-$i" },
            ) { _, e ->
              Column {
                when (e) {
                  is ErrorEvent.Tcp -> {
                    Text(
                        text = e.request?.host ?: "NO HOST",
                        style =
                            MaterialTheme.typography.body2.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                    )
                    Text(
                        text = e.throwable.message ?: "TCP Proxy Error",
                        style = MaterialTheme.typography.caption,
                    )
                    Text(
                        text = "${SharedProxy.Type.TCP.name}: ${e.request}",
                        style = MaterialTheme.typography.caption,
                    )
                  }
                  is ErrorEvent.Udp -> {
                    Text(
                        text = e.throwable.message ?: "UDP Proxy Error",
                        style = MaterialTheme.typography.caption,
                    )
                    Text(
                        text = SharedProxy.Type.UDP.name,
                        style = MaterialTheme.typography.caption,
                    )
                  }
                  else -> throw IllegalStateException("Clear events should not be propagated")
                }
              }
            }
          }
        }
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    lifecycleScope.launch(context = Dispatchers.Main) {
      wiDiNetwork?.stop()
      wiDiNetwork = null
    }
  }
}

@Composable
private fun DisplayStatus(status: RunningStatus) {
  val text =
      remember(status) {
        when (status) {
          is RunningStatus.Error -> "Error: ${status.message}"
          is RunningStatus.NotRunning -> "Not Running"
          is RunningStatus.Running -> "Running"
          is RunningStatus.Starting -> "Starting"
          is RunningStatus.Stopping -> "Stopping"
        }
      }

  val color =
      remember(status) {
        when (status) {
          is RunningStatus.Error -> Color.Red
          is RunningStatus.NotRunning -> Color.Unspecified
          is RunningStatus.Running -> Color.Green
          is RunningStatus.Starting -> Color.Cyan
          is RunningStatus.Stopping -> Color.Magenta
        }
      }

  Text(
      text = text,
      style = MaterialTheme.typography.body2,
      color = color,
  )
}