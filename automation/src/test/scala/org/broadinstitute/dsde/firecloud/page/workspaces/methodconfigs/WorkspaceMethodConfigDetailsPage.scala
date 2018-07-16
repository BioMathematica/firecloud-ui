package org.broadinstitute.dsde.firecloud.page.workspaces.methodconfigs

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import com.typesafe.scalalogging.LazyLogging
import org.broadinstitute.dsde.firecloud.{FireCloudConfig, FireCloudView}
import org.broadinstitute.dsde.firecloud.component._
import org.broadinstitute.dsde.firecloud.component.Component._
import org.broadinstitute.dsde.firecloud.page.workspaces.WorkspacePage
import org.broadinstitute.dsde.firecloud.page.workspaces.monitor.SubmissionDetailsPage
import org.broadinstitute.dsde.firecloud.page.PageUtil
import org.broadinstitute.dsde.workbench.service.util.Util
import org.openqa.selenium.{TimeoutException, WebDriver}
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.Eventually.eventually
import org.scalatest.selenium.Page

import scala.util.{Failure, Success, Try}

class WorkspaceMethodConfigDetailsPage(namespace: String, name: String, methodConfigNamespace: String, val methodConfigName: String)(implicit webDriver: WebDriver)
  extends WorkspacePage(namespace, name) with Page with PageUtil[WorkspaceMethodConfigDetailsPage] with LazyLogging with Eventually {

  override def awaitReady(): Unit = {
    await condition isLoaded
    await spinner "Checking permissions..."
  }

  override val url: String = s"${FireCloudConfig.FireCloud.baseUrl}#workspaces/$namespace/$name/method-configs/$methodConfigNamespace/$methodConfigName"

  private val openLaunchAnalysisModalButton = Button("open-launch-analysis-modal-button")
  private val openEditModeButton = Button("edit-method-config-button")
  private val editMethodConfigNameInput = TextField("edit-method-config-name-input")
  private val saveEditedMethodConfigButton = Button("save-edited-method-config-button")
  private val editMethodConfigSnapshotIdSelect = Select("edit-method-config-snapshot-id-select")
  private val editMethodConfigRootEntityTypeSelect = Select("edit-method-config-root-entity-type-select")
  private val deleteMethodConfigButton = Button("delete-method-config-button")
  private val modalConfirmDeleteButton = Button("modal-confirm-delete-button")
  private val snapshotRedactedLabel = Label("snapshot-redacted-title")
  private val dataModelCheckbox = Checkbox("data-model-checkbox")
  private val populateWithJsonLink = Link("populate-with-json-link")
  private val downloadInputsJsonLink = Link("download-link")
  val inputsTable = Table("inputs-table")


  def clickLaunchAnalysis[T <: FireCloudView](page: T): T = {
    openLaunchAnalysisModalButton.doClick()
    Try(
      page.awaitReady() // check if click happened
    ) match {
      case Failure(e) => // click failed
        openLaunchAnalysisModalButton.doClick()
        await ready page
      case Success(some) => // clicked
        page
    }
  }

  def launchAnalysis(rootEntityType: String, entityId: String, expression: String = "", enableCallCaching: Boolean = true): SubmissionDetailsPage = {
    val launchModal = openLaunchAnalysisModal()
    launchModal.launchAnalysis(rootEntityType, entityId, expression, enableCallCaching)
    try {
      launchModal.awaitDismissed()
      await ready new SubmissionDetailsPage(namespace, name)
    } catch {
      case e: TimeoutException =>
        if (webDriver.getPageSource.contains("Please select an entity")) {
          logger.warn(s"Retrying select participant $entityId and click Launch button in Launch Analysis modal")
          launchModal.searchAndSelectEntity(entityId)
          launchModal.clickLaunchButton()
          await ready new SubmissionDetailsPage(namespace, name)
        } else {
          throw e
        }
    }
  }

  def openLaunchAnalysisModal(): LaunchAnalysisModal = {
    clickLaunchAnalysis(new LaunchAnalysisModal())
  }

  def clickLaunchAnalysisButtonError(): MessageModal = {
    clickLaunchAnalysis(new MessageModal())
  }

  def isEditing: Boolean = {
    if (openEditModeButton.isVisible) false
    else if (saveEditedMethodConfigButton.isVisible) true
    else throw new Exception("Could not determine edit mode of method config view")
  }

  def openEditMode(expectSuccess: Boolean = true): Unit = {
    openEditModeButton.doClick()
    if (expectSuccess)
      saveEditedMethodConfigButton.awaitVisible()
  }

  def checkSaveButtonState(): String = {
    saveEditedMethodConfigButton.getState
  }

  def saveEdits(expectSuccess: Boolean = true): Unit = {
    // The button can sometimes scroll off the page and become unclickable. Therefore we need to scroll it into view.
    saveEditedMethodConfigButton.scrollToVisible()
    saveEditedMethodConfigButton.doClick()
    if (expectSuccess)
      openLaunchAnalysisModalButton.awaitVisible()
  }

  def editMethodConfig(newName: Option[String] = None, newSnapshotId: Option[Int] = None, newRootEntityType: Option[String] = None,
                       inputs: Option[Map[String, String]] = None, outputs: Option[Map[String, String]] = None): Unit = {
    openEditMode()
    await spinner "Loading attributes..."

    if (newName.isDefined) { editMethodConfigNameInput.setText(newName.get) }
    if (newSnapshotId.isDefined) { changeSnapshotId(newSnapshotId.get) }
    if (newRootEntityType.isDefined) { editMethodConfigRootEntityTypeSelect.select(newRootEntityType.get)}
    if (inputs.isDefined) { changeInputsOutputs(inputs.get) }
    if (outputs.isDefined) { changeInputsOutputs(outputs.get)}

    saveEdits()
  }

  def toggleDataModel(): Boolean = {
    if (dataModelCheckbox.isChecked) dataModelCheckbox.ensureUnchecked()
    else dataModelCheckbox.ensureChecked()
    dataModelCheckbox.isChecked
  }

  def readRootEntityTypeSelect: String = {
    editMethodConfigRootEntityTypeSelect.value
  }

  def selectRootEntityType(rootEntityType: String): Unit = {
    editMethodConfigRootEntityTypeSelect.select(rootEntityType)
  }

  def clickAndReadSuggestions(field: String): Seq[String] = {
    val dataTestId = s"$field-text-input"
    click on testId(dataTestId)
    // wait for dropdown to become expanded
    await condition (find(testId(dataTestId)).exists(_.underlying.getAttribute("aria-expanded") == "true"), 10)
    // wait for dropdown to contain at least one WebElement
    val xpathSelector = s"//*[@data-test-id='$field-suggestions']/*/li"
    await condition (findAll(xpath(xpathSelector)).map(elem => elem.isDisplayed).nonEmpty, 10)
    findAll(xpath(xpathSelector)).map(_.text).toSeq
  }

  def readFieldValue(field: String): String = {
    if (isEditing) searchField(testId(s"$field-text-input")).value
    else find(testId(s"$field-display")).map(_.underlying.getText).getOrElse("")
  }

  def populateInputsFromJson(file: File): Unit = {
    populateWithJsonLink.doClick()
    val modal = await ready new PopulateFromJsonModal
    modal.importFile(file.getAbsolutePath)
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
  def downloadInputsJson(downloadPath: Option[String] = None): Option[String] = synchronized {

    def archiveDownloadedFile(sourcePath: String): String = {
      // wait up to 10 seconds for file exist
      val f = new File(sourcePath)
      eventually {
        assert(f.exists(), s"Timed out (10 seconds) waiting for file $f")
      }

      val date = DateTimeFormatter.ofPattern(dateFormatPatter).format(LocalDateTime.now())
      val destFile = new File(sourcePath).getName + s".$date"
      val destPath = s"downloads/$destFile"
      Util.moveFile(sourcePath, destPath)
      logger.info(s"Moved file. sourcePath: $sourcePath, destPath: $destPath")
      destPath
    }

    downloadInputsJsonLink.awaitEnabled()

    /*
     * Downloading a file will open another window while the download is in progress and
     * automatically close it when the download is complete.
     */
    // await condition (windowHandles.size == 1, 30)
    // .submit call takess care waiting for a new window
    logger.info(s"form: ${form.queryString}")
    downloadInputsJsonLink.doClick()
    println("download link click")

    for {
      path <- downloadPath
    } yield archiveDownloadedFile(s"$path/inputs.json")
  }

  lazy val dateFormatPatter = "HH:mm:ss:N" // with nano seconds
  val form = CssSelectorQuery(s"${inputsTable.query.queryString} form")


  def changeSnapshotId(newSnapshotId: Int): Unit = {
    editMethodConfigSnapshotIdSelect.select(newSnapshotId.toString)
    await spinner "Updating..."
  }

  private def changeInputsOutputs(fields: Map[String, String]): Unit = {
    for ((field, expression) <- fields) {
      val fieldInputQuery: Query = xpath(s"//*[@data-test-id='$field-text-input']/..//input")
      searchField(fieldInputQuery).value = expression
    }
  }

  // TODO This is a very weak check to deterimine if page is ready
  def isLoaded: Boolean = {
    await spinner "Loading attributes..."
    openLaunchAnalysisModalButton.isVisible
  }

  def deleteMethodConfig(): WorkspaceMethodConfigListPage = {
    deleteMethodConfigButton.doClick()
    // TODO: make this a proper modal view
    modalConfirmDeleteButton.awaitVisible()
    modalConfirmDeleteButton.doClick()
    await ready new WorkspaceMethodConfigListPage(namespace, name)
  }

  def isSnapshotRedacted: Boolean = {
    snapshotRedactedLabel.isVisible
  }
}

class PopulateFromJsonModal(implicit webDriver: WebDriver) extends OKCancelModal("upload-json-modal") {
  private val fileInput = FileSelector("data-upload-input" inside this)
  private val uploadButton = Button("confirm-upload-metadata-button" inside this)

  override def awaitReady(): Unit = fileInput.awaitEnabled()

  def importFile(file: String): Unit = {
    fileInput.selectFile(file)
    await condition find(query).get.underlying.getText.contains("Previewing")
    uploadButton.doClick()
    await notVisible query
  }
}

/**
  * Page class for the launch analysis modal.
  */
class LaunchAnalysisModal(implicit webDriver: WebDriver) extends OKCancelModal("launch-analysis-modal") {
  override def awaitReady(): Unit = entityTable.awaitReady()

  private val entityTable = Table("entity-table" inside this)
  private val expressionInput = TextField("define-expression-input" inside this)
  private val noRowsMessage = Label("message-well" inside this)
  private val launchAnalysisButton = Button("launch-button" inside this)
  private val numberOfWorkflowsWarning = Label("number-of-workflows-warning" inside this)
  private val callCachingCheckbox = Checkbox("call-cache-checkbox" inside this)

  def launchAnalysis(rootEntityType: String, entityId: String, expression: String = "", enableCallCaching: Boolean): Unit = { //Use Option(String) for expression?
    logger.info(s"Selecting participant $entityId and click Launch button in Launch Analysis modal")
    filterRootEntityType(rootEntityType)
    searchAndSelectEntity(entityId)
    if (!expression.isEmpty) { fillExpressionField(expression) }
    if (!enableCallCaching) { callCachingCheckbox.ensureChecked() }
    clickLaunchButton()
  }

  def validateLocation: Boolean = {
    entityTable.isVisible
  }

  def filterRootEntityType(rootEntityType: String): Unit = {
    entityTable.goToTab(rootEntityType)
  }

  def searchAndSelectEntity(entityId: String): Unit = {
    entityTable.filter(entityId)
    Link(entityId + "-link" inside entityTable).doClick()
  }

  def fillExpressionField(expression: String): Unit = {
    expressionInput.setText(expression)
  }

  def clickLaunchButton(): Unit = {
    launchAnalysisButton.doClick()
  }

  def verifyNoRowsMessage(): Boolean = {
    noRowsMessage.isVisible
  }

  def verifyWorkflowsWarning(): Boolean = {
    numberOfWorkflowsWarning.isVisible
  }

  def verifyErrorText(errorText: String): Boolean = {
    isErrorTextPresent(errorText)
  }

  private def isErrorTextPresent(errorText: String): Boolean = {
    val errorTextQuery: Query = text(errorText)
    await enabled errorTextQuery
    val error = find(errorTextQuery)
    error.size == 1
  }
}





