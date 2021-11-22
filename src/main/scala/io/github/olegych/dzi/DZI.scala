package io.github.olegych.dzi

import com.google.common.io.{MoreFiles, RecursiveDeleteOption}
import io.github.olegych.dzi.imageio.ImageReader
import io.github.olegych.dzi.models._

import java.awt.{Point, Transparency}
import java.awt.color.ColorSpace
import java.awt.image.{BufferedImage, ComponentColorModel, DataBuffer, DataBufferByte, Raster}
import java.io.File
import java.nio.file.{Files, StandardOpenOption}
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong, AtomicReference}
import javax.imageio.ImageIO
import scala.collection.IterableOnce
import scala.collection.parallel.CollectionConverters._
import scala.collection.parallel.immutable.{ParRange, ParSeq}
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future, blocking}

case class ParallelConfig(
    cols: Boolean,
    rows: Boolean,
    levels: Boolean,
    tiles: Boolean,
    downscale: Int,
    crop: Int,
    write: Int,
    await: Int,
)

object ParallelConfig {
  //not too memory hungry, and close to the fastest for 1-12 parallel jobs
  val optimal = ParallelConfig(
    cols = false,
    rows = false,
    levels = true,
    tiles = true,
    downscale = 1024,
    crop = 1024,
    write = 0,
    await = 1024,
  )
  //fastest for single parallel job
  val fastest = ParallelConfig(
    cols = true,
    rows = false,
    levels = true,
    tiles = true,
    downscale = 1024,
    crop = 1024,
    write = 1024,
    await = 1024,
  )
  //least cpu intensive
  val easiest = ParallelConfig(
    cols = false,
    rows = false,
    levels = false,
    tiles = true,
    downscale = 10240,
    crop = 1024,
    write = 0,
    await = 1024,
  )
}

case class DZI(origSize: SizeInPx, tileSize: Int, overlap: Int, format: ImageFormat.Value) {
  def filesDir(name: String) = new File(s"${name}_files")

  case class Level(index: Int, size: SizeInPx, cells: SizeInCells) {
    theLevel =>
    def dir(name: String) = new File(filesDir(name), index.toString)

    val (rows, cols) = {
      val vertPxSize = origSize.pxHeight / size.pxHeight.toDouble
      val horizPxSize = origSize.pxWidth / size.pxWidth.toDouble
      val rows = for {
        i <- 0 until size.pxHeight
        start = Math.round(vertPxSize * i).toInt
        end = Math.round(vertPxSize * (i + 1)).toInt
        idx <- start until end
      } yield (end - 1) -> (end - start)
      val cols =
        for (i <- 0 until size.pxWidth)
          yield (Math.round(horizPxSize * i).toInt until Math.round(horizPxSize * (i + 1)).toInt).par

      (rows.toArray, cols.zipWithIndex.par)
    }

    case class Tile(col: Int, row: Int, size: SizeInPx, offset: OffsetInPx) {
      def level = theLevel

      def file(name: String) = new File(dir(name), s"${col}_${row}.$format")

      val unapply = level -> Tile.unapply(this)
    }

    val tiles = {
      for {
        col <- 0 until cells.cols
        row <- 0 until cells.rows
      } yield {
        val offsetXOverlap = if (col == 0) 0 else overlap
        val offsetYOverlap = if (row == 0) 0 else overlap
        val offset = OffsetInPx(
          pxX = (col * tileSize) - offsetXOverlap,
          pxY = (row * tileSize) - offsetYOverlap
        )
        val croppedTileSize = SizeInPx(
          pxWidth = (tileSize + overlap + offsetXOverlap).min(size.pxWidth - offset.pxX),
          pxHeight = (tileSize + overlap + offsetYOverlap).min(size.pxHeight - offset.pxY)
        )
        Tile(col = col, row = row, size = croppedTileSize, offset = offset)
      }
    }.par
  }

  val computeLevels = {
    var columns = 0.0
    var rows = 0.0
    var width = origSize.pxWidth.toDouble
    var height = origSize.pxHeight.toDouble
    val maxLevels = Math.ceil(Math.log(origSize.pxWidth.max(origSize.pxHeight)) / Math.log(2)).toInt
    (maxLevels to 0 by -1).map { level =>
      columns = Math.ceil(width / tileSize)
      rows = Math.ceil(height / tileSize)
      val l = Level(level, SizeInPx(width.toInt, height.toInt), SizeInCells(columns.toInt, rows.toInt))
      width = Math.ceil(width / 2)
      height = Math.ceil(height / 2)
      l
    }.par
  }

  def descriptor =
    s"""<?xml version="1.0" encoding="utf-8"?>
       |<Image TileSize="${tileSize}" Overlap="${overlap}" Format="${format}" xmlns="http://schemas.microsoft.com/deepzoom/2009">
       |<Size Width="${origSize.pxWidth}" Height="${origSize.pxHeight}" />
       |</Image>""".stripMargin

  case class create(
      inputFile: FileWithFormat,
      outputDir: File,
      outputName: String,
      debug: Boolean = true,
  ) {
    private def println(s: String) = if (debug) Predef.println(s)

    val filesOutputsDir = new File(outputDir, filesDir(outputName).getPath)
    try {
      MoreFiles.deleteRecursively(filesOutputsDir.toPath, RecursiveDeleteOption.ALLOW_INSECURE)
    } catch {
      case e: Throwable => //ignore
    }
    val descriptorFile = new File(outputDir, s"$outputName.dzi")
    outputDir.mkdirs()
    Files.write(descriptorFile.toPath, descriptor.getBytes("utf-8"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

    private lazy val ir = ImageReader.openFile(inputFile)
    private lazy val meta = ir.meta
    private def isource = new Iterator[Array[Byte]] {
      var read = 0

      def hasNext = if (ir.hasNext) true
      else {
        ir.close()
        false
      }

      def next() = {
        val buf = new Array[Byte](meta.size.pxWidth * meta.channels)
        ir.read(buf, 0)
        read += 1
        buf
      }
    }

    private case class tileImage(tile: Level#Tile) {
      private val tileFile = new File(outputDir, tile.file(outputName).getPath)
      tileFile.getParentFile.mkdirs()
      private val file = FileWithFormat(tileFile, FileFormat(format))
      private lazy val img = meta.channels match {
        case 1 =>
          new BufferedImage(
            tile.size.pxWidth,
            tile.size.pxHeight,
            if (meta.bitsPerChannel == 8) BufferedImage.TYPE_BYTE_GRAY else BufferedImage.TYPE_BYTE_BINARY
          )
        case 3 =>
          val width = tile.size.pxWidth
          val height = tile.size.pxHeight
          val cs = ColorSpace.getInstance(ColorSpace.CS_sRGB)
          val bOffs = Array(0, 1, 2) //rgb as produced by pngj
          val nBits = Array.tabulate(bOffs.length)(_ => meta.bitsPerChannel)
          val colorModel = new ComponentColorModel(cs, nBits, false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE)
          val raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, width, height, width * bOffs.length, bOffs.length, bOffs, null)
          new BufferedImage(colorModel, raster, false, null)
        case 4 =>
          val width = tile.size.pxWidth
          val height = tile.size.pxHeight
          val cs = ColorSpace.getInstance(ColorSpace.CS_sRGB)
          val bOffs = Array(0, 1, 2, 3) //rgba as produced by pngj
          val nBits = Array.tabulate(bOffs.length)(_ => meta.bitsPerChannel)
          val colorModel = new ComponentColorModel(cs, nBits, true, false, Transparency.TRANSLUCENT, DataBuffer.TYPE_BYTE)
          val raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, width, height, width * bOffs.length, bOffs.length, bOffs, null)
          new BufferedImage(colorModel, raster, false, null)
      }
      private lazy val sm = img.getSampleModel.createCompatibleSampleModel(tile.size.pxWidth, 1)
      private val written = new AtomicInteger(0)

      def write(row: Array[Byte], idx: Long)(implicit ec: ExecutionContext) = {
        val location = new Point(0, idx.toInt)
        val r = Raster.createRaster(sm, new DataBufferByte(row, row.length), location)
        img.setData(r)
        val done = written.incrementAndGet() == tile.size.pxHeight
        if (done) {
          Some(Future {
            blocking {
              println(s"wrote ${tileFile}")
              ImageIO.write(img, file.format.imageFormat.toString, tileFile)
            }
          })
        } else None
      }
    }

    def withPar(config: ParallelConfig = ParallelConfig.optimal)(implicit ec: ExecutionContext) = {
      implicit class MaybeParIterator[A, X](ts: Iterator[A]) {
        @inline def flatMapPar[B](par: Int)(f: A => IterableOnce[B]) = if (par > 0) ts.grouped(par).flatMap(_.par.flatMap(f)) else ts.flatMap(f)

        @inline def groupedMaybe[B](par: Int): Iterator[Seq[A]] = if (par > 0) ts.grouped(par) else ts.grouped(1)
      }

      implicit class MaybeParSeq[A, X](ts: ParSeq[A]) {
        @inline def foreachPar(par: Boolean)(f: A => Unit) = if (par) ts.foreach(f) else ts.seq.foreach(f)

        @inline def flatMapPar[B](par: Boolean)(f: A => IterableOnce[B]) = if (par) ts.flatMap(f) else ts.seq.flatMap(f)
      }

      val levels = computeLevels.map { level =>
        (level, (0 until meta.channels).map(channel => (new Array[Int](level.size.pxWidth), channel)), new AtomicInteger())
      }
      @inline def translateX(x: Int, channel: Int) = x * meta.channels + channel
      def downscaling(source: Iterator[Array[Byte]]) = source.zipWithIndex.groupedMaybe(config.downscale).flatMap { group =>
        levels.flatMapPar(config.levels) { case (level, temp, pxY) =>
          group.flatMap { case (origRow, idx) =>
            if (level.size == origSize) {
              Some((level, idx, origRow))
            } else {
              def update(level: Level, temp: Seq[(Array[Int], Int)], origRow: Array[Byte], idx: Int) = {
                def updateTemp(range: ParRange, destPos: Int) =
                  temp.foreach { case (temp, channel) =>
                    range.foreachPar(config.rows)(origPos => temp(destPos) += origRow(translateX(origPos, channel)) & 0xff)
                  }

                val rowRange = level.rows(idx)
                if (idx == rowRange._1) {
                  val scaledRow = new Array[Byte](level.size.pxWidth * meta.channels)
                  level.cols.foreachPar(config.cols) { case (range, destPos) =>
                    updateTemp(range, destPos)
                    temp.foreach { case (temp, channel) =>
                      scaledRow(translateX(destPos, channel)) = Math.round(temp(destPos).toDouble / (range.length * rowRange._2)).toByte
                    }
                  }
                  temp.foreach { case (temp, channel) =>
                    java.util.Arrays.fill(temp, 0)
                  }
                  Some(scaledRow)
                } else {
                  level.cols.foreachPar(config.cols) { case (range, destPos) =>
                    updateTemp(range, destPos)
                  }
                  None
                }
              }

              update(level, temp, origRow, idx).map { scaledRow =>
                (level, pxY.getAndIncrement(), scaledRow)
              }
            }
          }
        }
      }

      def crop(level: Level, pxY: Int, row: Array[Byte]) = level.tiles.flatMapPar(config.tiles) { tile =>
        val offset = tile.offset
        val size = tile.size
        if (pxY >= offset.pxY && pxY <= offset.pxY + size.pxHeight - 1) {
          Some((tile, pxY - offset.pxY, row.slice(translateX(offset.pxX, 0), translateX(offset.pxX + size.pxWidth + 1, meta.channels - 1))))
        } else None
      }

      val count = new AtomicLong(0)
      @transient var done = false
      val monitor = if (debug) {
        Some(
          new Thread(() =>
            while (!done) {
              Thread.sleep(1000)
              println(count.getAndSet(0).toString)
            }
          ) {
            setDaemon(true)
            start()
          }
        )
      } else None
      val sinks = new AtomicReference(
        levels.seq.flatMap(_._1.tiles).map(tile => tile.unapply -> tileImage(tile)).toMap
      )

      def run = downscaling(isource)
        .flatMapPar(config.crop) { case (level, pxY, row) => crop(level, pxY, row) }
        .flatMapPar(config.write) { case (tile, pxY, row) =>
          count.incrementAndGet()
          val tileImage = sinks.get().apply(tile.unapply)
          val done = tileImage.write(row, pxY)
          done.map { done =>
            done.map { _ =>
              sinks.updateAndGet(_.removed(tile.unapply))
              ()
            }
          }
        }
        .groupedMaybe(config.await)
        .foreach(r => Await.result(Future.sequence(r), Duration.Inf))

      try {
        run
        descriptorFile
      } finally {
        done = true
      }
    }
  }
}
