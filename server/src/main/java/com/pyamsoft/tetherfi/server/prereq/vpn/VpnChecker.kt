package com.pyamsoft.tetherfi.server.prereq.vpn

import androidx.annotation.CheckResult

interface VpnChecker {

  @CheckResult suspend fun isUsingVpn(): Boolean
}
