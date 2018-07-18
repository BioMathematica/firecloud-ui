package org.broadinstitute.dsde.firecloud.fixture

import com.typesafe.scalalogging.LazyLogging
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import org.broadinstitute.dsde.firecloud.component.Clickable
import org.broadinstitute.dsde.workbench.service.util.Util
import org.scalatest.concurrent.Eventually
import java.util.UUID

import org.broadinstitute.dsde.workbench.service.test.WebBrowserUtil
import org.openqa.selenium.WebDriver

trait DownloadFixtures extends Eventually with LazyLogging with WebBrowserUtil {


  def makeTempDownloadDirectory(): String = {
    /*
     * This might work some day if docker permissions get straightened out... or it might not be
     * needed. For now, we instead `chmod 777` the directory in run-tests.sh.
    new File("chrome").mkdirs()
    val downloadPath = Files.createTempDirectory(Paths.get("chrome"), "downloads")
    val permissions = Set(PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_WRITE, PosixFilePermission.OTHERS_WRITE)
    Files.setPosixFilePermissions(downloadPath, permissions.asJava)
    downloadPath.toString
     */

    val downloadPath = s"chrome/downloads/${UUID.randomUUID()}"
    val dir = new File(downloadPath)
    dir.deleteOnExit()
    dir.mkdirs()
    val path = dir.toPath
    logger.info(s"mkdir: $path")
    val permissions = Set(
      PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE,
      PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
      PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE)
    import scala.collection.JavaConverters._
    Files.setPosixFilePermissions(path, permissions.asJava)
    path.toString
  }

  /**
    * Downloads the metadata currently being viewed.
    *
    * If downloadPath is given, the file is given a timestamped name and moved from that location
    * into the "downloads" directory off the current working directory. This serves two purposes:
    *
    * 1. Archiving the file for later inspection when tests fail
    * 2. Keeping the browser download directory clean so that it doesn't auto-rename subsequent
    * downloads with the same filename
    *
    * @param downloadPath the directory where the browser saves downloaded files
    * @return the relative path to the moved download file, or None if downloadPath was not given
    */
  def downloadFile(downloadPath: String, fileName: String, downloadThing: Either[Clickable, CssSelectorQuery])(implicit webDriver: WebDriver): String = synchronized {

    def archiveDownloadedFile(sourcePath: String): String = {
      // wait up to 10 seconds for file exist
      val f = new File(sourcePath)
      eventually {
        assert(f.exists(), s"Timed out (10 seconds) waiting for file $f")
      }

      val date = DateTimeFormatter.ofPattern("HH:mm:ss:N").format(LocalDateTime.now())
      val destFile = new File(sourcePath).getName + s".$date"
      val destPath = s"downloads/$destFile"
      Util.moveFile(sourcePath, destPath)
      logger.info(s"Moved file. sourcePath: $sourcePath, destPath: $destPath")
      destPath
    }

    /*
     * Downloading a file will open another window while the download is in progress and
     * automatically close it when the download is complete.
     */
    // await condition (windowHandles.size == 1, 30)
    // .submit call takess care waiting for a new window

    downloadThing match {
      case Left(clickable) => clickable.doClick()
      case Right(query) => {
        logger.info(s"form: ${query.queryString}")
        find(query).get.underlying.submit()
      }
    }

    archiveDownloadedFile(s"$downloadPath/$fileName")
  }
}