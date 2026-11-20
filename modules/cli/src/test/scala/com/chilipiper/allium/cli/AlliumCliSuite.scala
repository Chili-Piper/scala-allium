package com.chilipiper.allium.cli

import java.nio.charset.StandardCharsets.US_ASCII
import java.nio.file.Files
import scala.jdk.CollectionConverters._

class AlliumCliSuite extends munit.FunSuite {

  test("the target follows the OS and architecture") {
    assertEquals(Target.of("Linux", "amd64"), Right(Target.LinuxX64))
    assertEquals(Target.of("Mac OS X", "aarch64"), Right(Target.MacArm64))
    assert(Target.of("Windows 11", "amd64").isLeft)
    assert(Target.of("Linux", "riscv64").isLeft)
  }

  test("every known release pins a checksum for every target") {
    Release.known.foreach { r =>
      assertEquals(r.checksums.keySet, Set(Target.LinuxX64, Target.LinuxArm64, Target.MacX64, Target.MacArm64))
    }
    assertEquals(Release("3.6.1"), Some(Release.v3_6_1))
  }

  /** A `.tar.gz` made by the system `tar` from `files`, as a release archive is. */
  private def archive(files: (String, String)*): Array[Byte] = {
    val dir = Files.createTempDirectory("allium-archive")
    files.foreach { case (name, text) => Files.writeString(dir.resolve(name), text) }
    val out = dir.resolve("out.tar.gz")
    val p =
      new ProcessBuilder(("tar" :: "-czf" :: out.toString :: "-C" :: dir.toString :: files.map(_._1).toList).asJava)
        .inheritIO()
        .start()
    assertEquals(p.waitFor(), 0)
    Files.readAllBytes(out)
  }

  test("the binary is taken out of a release archive and made executable") {
    val bin = Files.createTempDirectory("allium-cli").resolve("3.0.0/allium")
    assertEquals(AlliumCli.install(archive("README" -> "hello", "allium" -> "binary"), bin), Right(()))
    assertEquals(Files.readString(bin), "binary")
    assert(Files.isExecutable(bin))
    assertEquals(Files.list(bin.getParent).count(), 1L)
  }

  test("an archive without the binary, or not an archive, leaves no binary") {
    val bin = Files.createTempDirectory("allium-cli").resolve("3.0.0/allium")
    assertEquals(AlliumCli.install(archive("README" -> "hello"), bin), Left("no allium in the archive"))
    assert(AlliumCli.install("not a tar".getBytes(US_ASCII), bin).left.exists(_.startsWith("tar cannot unpack")))
    assert(!Files.exists(bin))
    assertEquals(Files.list(bin.getParent).count(), 0L)
  }

  test("a download with the wrong checksum is refused and leaves no binary") {
    val dir = Files.createTempDirectory("allium-cli")
    val wrong = Release.latest.copy(checksums = Release.latest.checksums.map { case (t, _) => t -> ("0" * 64) })
    val result = AlliumCli.download(wrong, dir)
    assert(result.left.exists(_.contains("expected 0000")), result)
    assert(!Files.exists(dir.resolve(wrong.version).resolve("allium")))
  }

  test("the pinned release downloads, runs, and reports through stdout even on a non-zero exit") {
    val cli = TestCli.cli
    val ok = cli.parseText("-- allium: 3\nrule A {\n    when: X()\n    ensures: Done()\n}\n")
    assert(ok.exists(_.contains("\"declarations\"")), ok)
    val broken = cli.parseText("rule A {\n")
    assert(broken.exists(_.contains("expected '}'")), broken)
    assert(AlliumCli.at(cli.binary).isRight)
  }

}
