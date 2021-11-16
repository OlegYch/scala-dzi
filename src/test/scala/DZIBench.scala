import com.google.common.io.{MoreFiles, RecursiveDeleteOption}
import org.olegych.dzi.imageio.ImageReader
import org.olegych.dzi.models._
import org.olegych.dzi.{DZI, ParallelConfig}

import java.io.File
import java.lang.management.ManagementFactory
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
//  val sizes = List(1, 2, 6, 12)
  val sizes = List(6, 12)
  val configs = for {
    cols <- List(false)
    rows = false
    //    rows <- bools
    levels <- bools
    tiles <- bools
    downscale <- ints
    crop <- List(0, 1024)
    write <- List(0)
    await = 1024
    //    await <- ints
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
  val bean = ManagementFactory.getPlatformMXBean(classOf[com.sun.management.OperatingSystemMXBean])
  for {
    size <- sizes
    c <- configs
  } {
    val start = Instant.now()
    val cpuStart = bean.getProcessCpuTime
    val configString = List(
      "cols" -> c.cols,
      "rows" -> c.rows,
      "levels" -> c.levels,
      "tiles" -> c.tiles,
      "downscale" -> c.downscale,
      "crop" -> c.crop,
      "write" -> c.write,
      "await" -> c.await,
    ).map { case (k, v) => s"${k}=,${v}," }.mkString
    @transient var failed = false
    val threads = (0 until size).map { _ =>
      val outputFolder = new File(rootOutput, Random.nextInt().toString)
      outputFolder -> new Thread(() => {
        try {
          dzi.create(ColorDepth.Greyscale, outputFolder, "createDZI", debug = false).withPar(input, c)
        } catch {
          case e: Throwable =>
            println(s"${configString} failed with ${e.getMessage}")
            failed = true
        }
      }) {
        start()
      }
    }
    threads.foreach { case (outputFolder, thread) =>
      try {
        if (!failed) thread.join() else {
          thread.interrupt()
          thread.join()
        }
      } catch {
        case e: Throwable =>
          println(e.getMessage)
      }
    }
    val cpuTotal = (bean.getProcessCpuTime - cpuStart) / 1000 / 1000
    val totalPerProcess = (cpuTotal.toDouble / size).toInt
    if (!failed) {
      println(s"At ${Instant.now} ${configString} for size ,${size}, took ,${Instant.now.getEpochSecond - start.getEpochSecond}, cpu total ,$cpuTotal, per process ,$totalPerProcess")
    }
    threads.foreach { case (outputFolder, thread) =>
      try {
        MoreFiles.deleteRecursively(outputFolder.toPath, RecursiveDeleteOption.ALLOW_INSECURE)
      } catch {
        case e: Throwable => //ignore
      }
    }
  }
}
