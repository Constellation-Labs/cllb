package org.constellation.primitives.node

object State extends Enumeration {
  type State = Value

  val PendingDownload, DownloadInProgress, DownloadCompleteAwaitingFinalSync, Ready, Leaving, Offline =
    Value


}
