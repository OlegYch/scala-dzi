package io.github.olegych.dzi.models

import java.io.File

case class SizeInPx(pxWidth: Int, pxHeight: Int) {
  require(pxWidth > 0 && pxHeight > 0)

  def aspectRatio = pxWidth / pxHeight.toDouble

  def size = pxWidth * pxHeight

  def scale(ratio: Double) = new SizeInPx(Math.round(pxWidth * ratio).toInt, Math.round(pxHeight * ratio).toInt)
}

case class OffsetInPx(pxX: Int, pxY: Int)

case class SizeInCells(cols: Int, rows: Int) {
  def size = cols * rows
}

case class OffsetInCells(cols: Int, rows: Int)

case class AreaInCells(cellOffset: OffsetInCells, cellSize: SizeInCells)

case class PPM(horiz: Int, vert: Int)

object PPM {
  def apply(ppm: Int): PPM = PPM(ppm, ppm)
}

object ColorDepth extends Enumeration {
  val BlackAndWhite = Value(1)
  val Greyscale = Value(8)
}

case class ImageMetadata(size: SizeInPx, ppm: PPM, colorDepth: ColorDepth.Value)

case class FileFormat(imageFormat: ImageFormat.Value)

object FileFormat {

  def fromPartialFilename(partialFileName: String): FileFormat = {
    FileFormat(ImageFormat.fromPartialFilename(partialFileName))
  }

  def fromFile(file: File): FileFormat = fromPartialFilename(file.getName)
}

case class FileWithFormat(file: File, format: FileFormat)

object FileWithFormat {
  def apply(file: File): FileWithFormat = FileWithFormat(file, FileFormat.fromFile(file))

  def apply(dir: File, partialName: String, format: FileFormat): FileWithFormat = FileWithFormat(new File(dir, s"$partialName.$format"), format)
}

object ImageFormat extends Enumeration {

  val JPG = Value("jpg")
  val BMP = Value("bmp")
  val PNG = Value("png")

  def fromPartialFilename(partialFileName: String): Value = withName(partialFileName.reverse.takeWhile(_ != '.').reverse.toLowerCase)

  def fromFile(file: File): Value = fromPartialFilename(file.getName)

}
