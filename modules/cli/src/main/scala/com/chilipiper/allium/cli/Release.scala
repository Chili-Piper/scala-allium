package com.chilipiper.allium.cli

/** A platform that `juxt/allium-tools` publishes a CLI binary for. */
final case class Target(value: String) extends AnyVal {
  override def toString: String = value
}

object Target {
  val LinuxX64: Target = Target("x86_64-unknown-linux-gnu")
  val LinuxArm64: Target = Target("aarch64-unknown-linux-gnu")
  val MacX64: Target = Target("x86_64-apple-darwin")
  val MacArm64: Target = Target("aarch64-apple-darwin")

  /** The target of this JVM's OS and architecture. */
  def current: Either[String, Target] = of(sys.props.getOrElse("os.name", ""), sys.props.getOrElse("os.arch", ""))

  def of(osName: String, osArch: String): Either[String, Target] = {
    val os = osName.toLowerCase
    val arch = osArch match {
      case "amd64" | "x86_64"  => Right("x86_64")
      case "aarch64" | "arm64" => Right("aarch64")
      case other               => Left(s"allium: unsupported architecture $other")
    }
    arch.flatMap { a =>
      if (os.contains("linux"))
        Right(Target(s"$a-unknown-linux-gnu"))
      else if (os.contains("mac"))
        Right(Target(s"$a-apple-darwin"))
      else
        Left(s"allium: unsupported OS $osName")
    }
  }
}

/** A release of the `allium` CLI with the SHA-256 of each archive. `juxt/allium-tools` publishes no checksums, so they
  * are pinned here (or by the caller, for a release this library does not know).
  */
final case class Release(version: String, checksums: Map[Target, String]) {
  def url(target: Target): String =
    s"https://github.com/juxt/allium-tools/releases/download/v$version/allium-$target.tar.gz"
}

object Release {

  val v3_6_1: Release = Release(
    "3.6.1",
    Map(
      Target.LinuxX64 -> "e00c99ae234b10207719257a70e7c2dcad73060864468cd293fb7ad40524e970",
      Target.LinuxArm64 -> "e5ce96393db198b4f7d23ccd2494573e753936cc7b03ca1ecbac3cb20f993ff4",
      Target.MacX64 -> "db7f387a10f7449dd7da578fdf211fd36b3105f148412788b43aac9b76ccbe1c",
      Target.MacArm64 -> "ecfae02fcf8e60475a014944183158ccde4104f6f0223e1bf91f98519fcb19eb",
    ),
  )

  val v3_7_2: Release = Release(
    "3.7.2",
    Map(
      Target.LinuxX64 -> "f299548d8401a761ea5059abd202d1918da4c4ec445dc6c62db287755bf961da",
      Target.LinuxArm64 -> "6a21afc1347bfcdb82e566ddf696e1e0b5aa56fe4acbc3428bd29f33c00a2465",
      Target.MacX64 -> "15bf4e8e46fb76634572a03b90b9ccb7c53bb3a05db8cbd5080634c783911393",
      Target.MacArm64 -> "3de3acfbc7874d92c63ce4fbb92ff4333348743471b885ed15318e9401f6fc36",
    ),
  )

  val known: List[Release] = List(v3_6_1, v3_7_2)

  val latest: Release = v3_7_2

  def apply(version: String): Option[Release] = known.find(_.version == version)
}
