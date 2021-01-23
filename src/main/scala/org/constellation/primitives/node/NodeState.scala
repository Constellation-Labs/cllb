package org.constellation.primitives.node

object NodeState extends Enumeration {
  type State = Value

  val PendingDownload, DownloadInProgress, DownloadCompleteAwaitingFinalSync, Ready, Leaving, Offline, SnapshotCreation,
      ReadyForDownload = Value

}
