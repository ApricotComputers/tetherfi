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

package com.pyamsoft.tetherfi.server.clients

import androidx.annotation.CheckResult
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import java.time.LocalDateTime

@Stable
@Immutable
sealed class TetherClient(
    open val mostRecentlySeen: LocalDateTime,
) {
  data class IpAddress(
      val ip: String,
      override val mostRecentlySeen: LocalDateTime,
  ) :
      TetherClient(
          mostRecentlySeen = mostRecentlySeen,
      )

  data class HostName(
      val hostname: String,
      override val mostRecentlySeen: LocalDateTime,
  ) :
      TetherClient(
          mostRecentlySeen = mostRecentlySeen,
      )
}

@CheckResult
fun TetherClient.key(): String {
  return when (this) {
    is TetherClient.HostName -> this.hostname
    is TetherClient.IpAddress -> this.ip
  }
}
