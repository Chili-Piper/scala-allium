package com.chilipiper.allium.cli

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets.US_ASCII
import java.nio.file.Files

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

  private def tarEntry(name: String, data: Array[Byte]): Array[Byte] = {
    val header = new Array[Byte](512)
    def put(offset: Int, s: String): Unit = {
      val _ = s.getBytes(US_ASCII).copyToArray(header, offset)
    }
    put(0, name)
    put(124, f"${data.length.toOctalString}%11s".replace(' ', '0'))
    header(156) = '0'.toByte
    val padding = new Array[Byte]((512 - data.length % 512) % 512)
    header ++ data ++ padding
  }

  test("a file is taken out of a tar archive, skipping the entries before it") {
    val archive = tarEntry("README", "hello".getBytes(US_ASCII)) ++ tarEntry("./allium", "binary".getBytes(US_ASCII)) ++
      new Array[Byte](1024)
    assertEquals(
      Tar.extract(new ByteArrayInputStream(archive), "allium").map(new String(_, US_ASCII)),
      Right("binary"),
    )
    assert(Tar.extract(new ByteArrayInputStream(new Array[Byte](1024)), "allium").isLeft)
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
