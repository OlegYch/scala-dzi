package org.olegych.dzi.imageio

import java.io.{BufferedInputStream, File, FileInputStream, InputStream}
import ar.com.hjg.pngj.PngReaderByte
import com.alexdupre.bmp.BMPReader
import org.olegych.dzi.models._

trait ImageReader extends AutoCloseable {
  def meta: ImageMetadata

  def hasNext: Boolean

  def read(buf: Array[Byte], off: Int): Unit
}

object ImageReader {

  def openFile(file: File): ImageReader = openFile(FileWithFormat(file))

  def openFile(file: FileWithFormat): ImageReader = openStream(new BufferedInputStream(new FileInputStream(file.file)), file.format.imageFormat)

  def openStream(in: InputStream, format: ImageFormat.Value): ImageReader =
    format match {
      case ImageFormat.BMP =>
        new ImageReader {
          val bmp = new BMPReader(in)
          require(bmp.info.colorDepth == 1 || bmp.info.colorDepth == 8)

          override def meta =
            ImageMetadata(SizeInPx(bmp.info.width, bmp.info.height), PPM(bmp.info.horizPPM, bmp.info.vertPPM), ColorDepth(bmp.info.colorDepth))

          override def hasNext: Boolean = bmp.hasNextLine()

          override def read(buf: Array[Byte], off: Int): Unit = bmp.readIndexLine(buf, off)

          override def close(): Unit = bmp.close(false)
        }

      case ImageFormat.PNG =>
        new ImageReader {
          val png = new PngReaderByte(in)
          require((png.imgInfo.greyscale || png.imgInfo.indexed) && (png.imgInfo.bitDepth == 1 || png.imgInfo.bitDepth == 8))
          png.setMaxTotalBytesRead(5 * 1024 * 1024 * 1024) // raise limit to 5GB

          override def meta =
            ImageMetadata(
              SizeInPx(png.imgInfo.cols, png.imgInfo.rows),
              PPM(png.getMetadata.getDpm()(0).toInt, png.getMetadata.getDpm()(1).toInt),
              ColorDepth(png.imgInfo.bitDepth)
            )

          override def hasNext: Boolean = png.hasMoreRows

          override def read(buf: Array[Byte], off: Int): Unit = {
            val line = png.readRowByte().getScanline
            System.arraycopy(line, 0, buf, off, line.length)
          }

          override def close(): Unit = png.end()
        }

      case unsupported => sys.error("Unsupported file extension: " + unsupported)
    }

}
