package com.chilipiper.allium.cli

import java.nio.file.Paths

/** The pinned CLI for tests, downloaded once into `target/allium-cli`. */
object TestCli {
  lazy val cli: AlliumCli =
    AlliumCli
      .download(Release.latest, Paths.get(sys.props("user.dir"), "target", "allium-cli").toAbsolutePath)
      .fold(e => throw new IllegalStateException(e), identity)
}
