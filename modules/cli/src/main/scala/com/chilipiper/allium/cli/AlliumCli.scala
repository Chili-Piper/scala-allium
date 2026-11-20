package com.chilipiper.allium.cli

import java.io.{ByteArrayOutputStream, InputStream}
import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.{Files, Path, StandardCopyOption}
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

/** A runnable `allium` CLI binary.
  *
  * Get one with `AlliumCli.download` (pinned release, checksum-verified) or `AlliumCli.at` (an existing binary).
  */
final class AlliumCli private (val binary: Path) {

  /** Runs `allium <args>` in `cwd`. A non-zero exit is not an error: see `AlliumCli.Result.json`. */
  def run(cwd: Path, args: String*): Either[String, AlliumCli.Result] =
    try {
      val process = new ProcessBuilder((binary.toString +: args).asJava).directory(cwd.toFile).start()
      process.getOutputStream.close()
      val stderr = new ByteArrayOutputStream
      val errReader = new Thread(() => { process.getErrorStream.transferTo(stderr); () })
      errReader.start()
      val stdout = new String(process.getInputStream.readAllBytes(), UTF_8)
      val code = process.waitFor()
      errReader.join()
      Right(AlliumCli.Result(args.toList, code, stdout, new String(stderr.toByteArray, UTF_8)))
    } catch {
      case NonFatal(e) => Left(s"cannot run $binary ${args.mkString(" ")}: $e")
    }

  /** `allium check <path>`: one JSON object per spec file. */
  def check(cwd: Path, path: String): Either[String, String] = run(cwd, "check", path).flatMap(_.json)

  /** `allium plan <spec>`: the obligations of one spec. */
  def plan(cwd: Path, spec: String): Either[String, String] = run(cwd, "plan", spec).flatMap(_.json)

  /** `allium parse <spec>`: the AST of one spec. */
  def parse(cwd: Path, spec: String): Either[String, String] = run(cwd, "parse", spec).flatMap(_.json)

  /** `allium parse` of a spec given as text (for tests on synthetic specs). */
  def parseText(text: String): Either[String, String] = {
    val dir = Files.createTempDirectory("allium")
    val file = dir.resolve("spec.allium")
    try {
      Files.writeString(file, text)
      parse(dir, file.getFileName.toString)
    } finally {
      Files.deleteIfExists(file)
      Files.deleteIfExists(dir)
      ()
    }
  }

  override def toString: String = s"AlliumCli($binary)"
}

object AlliumCli {

  /** The output of one CLI run. */
  final case class Result(args: List[String], exitCode: Int, stdout: String, stderr: String) {

    /** The JSON report on stdout. The CLI exits non-zero on warnings (`check`) and on error diagnostics (`plan`,
      * `parse`) and still prints the report, so only an empty stdout is an error.
      */
    def json: Either[String, String] =
      if (stdout.trim.nonEmpty)
        Right(stdout)
      else
        Left(s"allium ${args.mkString(" ")} exited with $exitCode and printed nothing:\n$stderr")
  }

  /** An existing binary, for example one on the `PATH` or built locally. */
  def at(binary: Path): Either[String, AlliumCli] =
    if (Files.isExecutable(binary))
      Right(new AlliumCli(binary))
    else
      Left(s"$binary is not an executable file")

  /** The binary of `release` for this machine, downloaded into `cacheDir/<version>/` on first use. The archive must
    * match the pinned SHA-256; the binary is not downloaded again while it is in the cache.
    */
  def download(release: Release, cacheDir: Path): Either[String, AlliumCli] =
    Target.current.flatMap { target =>
      val bin = cacheDir.resolve(release.version).resolve("allium")
      if (Files.isExecutable(bin))
        Right(new AlliumCli(bin))
      else
        for {
          expected <- release.checksums.get(target).toRight(s"allium ${release.version}: no checksum for $target")
          _ <- fetch(release.url(target), expected, bin)
        } yield new AlliumCli(bin)
    }

  private def fetch(url: String, expectedSha256: String, bin: Path): Either[String, Unit] =
    try {
      val archive = readAll(URI.create(url).toURL.openStream())
      val actual = sha256(archive)
      if (actual != expectedSha256)
        Left(s"allium: $url has SHA-256 $actual, expected $expectedSha256")
      else
        Tar.extract(new GZIPInputStream(new java.io.ByteArrayInputStream(archive)), "allium").map { bytes =>
          Files.createDirectories(bin.getParent)
          val tmp = Files.createTempFile(bin.getParent, "allium", ".tmp")
          Files.write(tmp, bytes)
          Files.setPosixFilePermissions(
            tmp,
            Set(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE,
              PosixFilePermission.GROUP_READ,
              PosixFilePermission.GROUP_EXECUTE,
              PosixFilePermission.OTHERS_READ,
              PosixFilePermission.OTHERS_EXECUTE,
            ).asJava,
          )
          Files.move(tmp, bin, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
          ()
        }
    } catch {
      case NonFatal(e) => Left(s"allium: cannot download $url: $e")
    }

  private def readAll(in: InputStream): Array[Byte] =
    try in.readAllBytes()
    finally in.close()

  private[cli] def sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map("%02x".format(_)).mkString

}
