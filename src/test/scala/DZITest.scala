import io.github.olegych.dzi.imageio.ImageReader
import io.github.olegych.dzi.models._
import io.github.olegych.dzi.{DZI, ParallelConfig}
import org.specs2.matcher.ContentMatchers
import org.specs2.mutable.Specification

import java.io.File
import scala.concurrent.ExecutionContext.Implicits.global

class DZITest extends Specification with ContentMatchers {
  val testFiles = new File("src/test/resources").listFiles().filter { file =>
    file.getName.endsWith(".png")
  }
//  val testFiles = Array((new File("src/test/resources/bench/bench.png")))
  testFiles.map { input =>
    val tileSize = 256
    val tileOverlap = 1
    val tileFormat = ImageFormat.PNG
    val outputFolder = new File(s"/tmp/target/dzi/${input.getName}" /* + scala.util.Random.nextInt()*/ )
    s"create DZI from ${input.getName}" in {
      val meta = ImageReader.openFile(input).meta
      val c = ParallelConfig(
        cols = true,
        rows = false,
        levels = true,
        tiles = true,
        downscale = 1024,
        crop = 1024,
        write = 1024,
        await = 1024,
      )
      val dzi = DZI(meta.size, tileSize, tileOverlap, tileFormat).create(FileWithFormat(input), outputFolder, input.getName)
      val _ = dzi.withPar(c)
      outputFolder must haveSameFilesAs(new File(s"src/test/resources/dzi/${input.getName}")).copy(filesMatcher = haveSameMD5)
    }
  }
  "levels" in {
    val tileSize = 256
    val tileFormat = ImageFormat.PNG
    val dzi = DZI(SizeInPx(4224, 3168), tileSize, 1, tileFormat)
    dzi.computeLevels.seq ==== Vector(
      dzi.Level(13, SizeInPx(4224, 3168), SizeInCells(17, 13)),
      dzi.Level(12, SizeInPx(2112, 1584), SizeInCells(9, 7)),
      dzi.Level(11, SizeInPx(1056, 792), SizeInCells(5, 4)),
      dzi.Level(10, SizeInPx(528, 396), SizeInCells(3, 2)),
      dzi.Level(9, SizeInPx(264, 198), SizeInCells(2, 1)),
      dzi.Level(8, SizeInPx(132, 99), SizeInCells(1, 1)),
      dzi.Level(7, SizeInPx(66, 50), SizeInCells(1, 1)),
      dzi.Level(6, SizeInPx(33, 25), SizeInCells(1, 1)),
      dzi.Level(5, SizeInPx(17, 13), SizeInCells(1, 1)),
      dzi.Level(4, SizeInPx(9, 7), SizeInCells(1, 1)),
      dzi.Level(3, SizeInPx(5, 4), SizeInCells(1, 1)),
      dzi.Level(2, SizeInPx(3, 2), SizeInCells(1, 1)),
      dzi.Level(1, SizeInPx(2, 1), SizeInCells(1, 1)),
      dzi.Level(0, SizeInPx(1, 1), SizeInCells(1, 1))
    )
  }
}
