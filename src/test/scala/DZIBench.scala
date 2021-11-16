import com.google.common.io.{MoreFiles, RecursiveDeleteOption}
import org.olegych.dzi.imageio.ImageReader
import org.olegych.dzi.models._
import org.olegych.dzi.{DZI, ParallelConfig}
import org.specs2.matcher.ContentMatchers
import org.specs2.mutable.Specification

import java.io.File
import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

object DZIBench extends App {
  //  val input = FileWithFormat(new File("totenpass-combined.png"))
  val input = FileWithFormat(new File("src/test/resources/test.png"))
  val tileSize = 256
  val tileOverlap = 1
  val tileFormat = ImageFormat.PNG
  val meta = ImageReader.openFile(input).meta
  val dzi = DZI(meta.size, tileSize, tileOverlap, tileFormat)
  val bools = List(false, true)
  val ints = List(0, 1024, 10240)
  val sizes = List(1, 2, 6, 12)
  val configs = for {
    cols <- bools
    rows = false
    //    rows <- bools
    levels <- bools
    tiles <- bools
    downscale <- ints
    crop <- ints
    write <- ints
    await <- ints
  } yield ParallelConfig(
    cols = cols,
    rows = rows,
    levels = levels,
    tiles = tiles,
    downscale = downscale,
    crop = crop,
    write = write,
    await = await,
  )
  println(configs.size)
  val rootOutput = new File("/tmp/target/dzi")
  val outputFolder = new File(rootOutput, Random.nextInt().toString)
  val warmup = dzi.create(ColorDepth.Greyscale, outputFolder, "createDZI", debug = false).withPar(input, configs.last)
  for {
    size <- sizes
    c <- configs
  } {
    val start = Instant.now()
    val threads = (0 until size).map { _ =>
      val outputFolder = new File(rootOutput, Random.nextInt().toString)
      outputFolder -> new Thread(() => {
        dzi.create(ColorDepth.Greyscale, outputFolder, "createDZI", debug = false).withPar(input, c)
      }) {
        start()
      }
    }
    threads.foreach { case (outputFolder, thread) =>
      thread.join()
    }
    println(s"${c} for size ${size} took ${Instant.now.getEpochSecond - start.getEpochSecond}")
    threads.foreach { case (outputFolder, thread) =>
      try {
        MoreFiles.deleteRecursively(outputFolder.toPath, RecursiveDeleteOption.ALLOW_INSECURE)
      } catch {
        case e: Throwable => //ignore
      }
    }
  }
}
