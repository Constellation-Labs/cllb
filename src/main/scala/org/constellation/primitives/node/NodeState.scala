package org.constellation.primitives.node

object NodeState extends Enumeration {
  type State = Value

  val Initial, ReadyToJoin, LoadingGenesis, GenesisReady, RollbackInProgress, RollbackDone, StartingSession,
      SessionStarted, WaitingForDownload, DownloadInProgress, Observing, Ready, Leaving, Offline = Value

}
