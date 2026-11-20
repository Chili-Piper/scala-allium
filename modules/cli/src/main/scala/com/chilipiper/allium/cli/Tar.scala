package com.chilipiper.allium.cli

import java.io.InputStream
import java.nio.charset.StandardCharsets.US_ASCII

/** A minimal ustar reader: enough to take one regular file out of a release archive without a `tar` binary. */
private[cli] object Tar {

  private val BlockSize = 512

  def extract(in: InputStream, name: String): Either[String, Array[Byte]] =
    try loop(in, name)
    finally in.close()

  @annotation.tailrec
  private def loop(in: InputStream, name: String): Either[String, Array[Byte]] = {
    val header = in.readNBytes(BlockSize)
    if (header.length < BlockSize || header.forall(_ == 0))
      Left(s"no $name in the archive")
    else {
      val entryName = field(header, 0, 100).stripPrefix("./")
      val prefix = field(header, 345, 155)
      val fullName = if (prefix.isEmpty) entryName else s"$prefix/$entryName"
      val size = java.lang.Long.parseLong(field(header, 124, 12).trim match { case "" => "0"; case s => s }, 8)
      val typeFlag = header(156).toChar
      val padded = (size + BlockSize - 1) / BlockSize * BlockSize
      if (fullName == name && (typeFlag == '0' || typeFlag == '\u0000')) {
        val data = in.readNBytes(size.toInt)
        if (data.length < size) Left(s"$name is truncated in the archive") else Right(data)
      } else {
        in.skipNBytes(padded)
        loop(in, name)
      }
    }
  }

  private def field(header: Array[Byte], offset: Int, length: Int): String = {
    val bytes = header.slice(offset, offset + length).takeWhile(_ != 0)
    new String(bytes, US_ASCII)
  }

}
