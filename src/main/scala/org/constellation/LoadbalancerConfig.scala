package org.constellation

import org.constellation.primitives.node.Addr
import org.http4s.BasicCredentials

import scala.concurrent.duration.FiniteDuration

case class LoadbalancerConfig(
    `if`: String,
    port: Int,
    settingsPort: Int,
    networkNodes: Set[Addr],
    networkCredentials: Option[BasicCredentials],
    retryAfterMinutes: Int,
    clusterUpdateRoundDelay: FiniteDuration
)
