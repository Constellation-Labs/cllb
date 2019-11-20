package org.constellation

import java.net.InetAddress

import org.constellation.primitives.node.Addr
import pureconfig.ConfigReader

case class LoadbalancerConfig(
  `if`: String,
  port: Int,
  networkNodes: Set[Addr]
)
