package org.broadinstitute.dsde.firecloud.pages

import org.broadinstitute.dsde.firecloud.{Config, PageUtil}
import org.openqa.selenium.WebDriver
import org.scalatest.selenium.Page

class MethodConfigDetailsPage(namespace: String, name: String, methodConfigNamespace: String, methodConfigName: String)(implicit webDriver: WebDriver) extends WorkspacePage with Page with PageUtil[MethodConfigDetailsPage] {

  override val url: String = s"${Config.FireCloud.baseUrl}#workspaces/$namespace/$name/method-configs/$methodConfigNamespace/$methodConfigName"

  def launchAnalysis(rootEntityType: String, entityId: String) = {
    val launchModal = gestures.openLaunchAnalysisModal()
    launchModal.launchAnalysis(rootEntityType, entityId)
    new SubmissionDetailsPage(namespace, name)
  }

  def editMethodConfig(newName: Option[String] = None, newSnapshotId: Option[Int] = None, newRootEntityType: Option[String] = None,
                       inputs: Option[Map[String, String]] = None, outputs: Option[Map[String, String]] = None) = {
    gestures.openEditMode()
    if (newName != None) { gestures.changeMethodConfigName(newName.get) }
    if (newSnapshotId != None) { gestures.changeSnapshotId(newSnapshotId.get) }
    if (newRootEntityType != None) { gestures.changeRootEntityType(newRootEntityType.get)}
    if (inputs != None) { gestures.changeInputsOutputs(inputs.get)  }
    if (outputs != None) { gestures.changeInputsOutputs(outputs.get)}
    gestures.saveEdits()

  }

  def openlaunchModal() = {
    gestures.openLaunchAnalysisModal()
  }

  object gestures {
    private val methodConfigNameTextQuery: Query = testId("method-config-name")
    private val openLaunchAnalysisModalButtonQuery: Query = testId("open-launch-analysis-modal-button")
    private val openEditModeQuery: Query = testId("edit-method-config-button")
    private val editMethodConfigNameInputQuery: Query = testId("edit-method-config-name-input")
    private val saveEdittedMethodConfigButtonQuery: Query = testId("save-editted-method-config-button")
    private val cancelEditMethodConfigModeButtonQuery: Query = testId("cancel-edit-method-config-button")
    private val editMethodConfigSnapshotIdSelectQuery: Query = testId("edit-method-config-snapshot-id-select")
    private val editMethodConfigRootEntityTypeInputQuery: Query = testId("edit-method-config-root-entity-type-select")

    def openLaunchAnalysisModal(): LaunchAnalysisModal = {
      await enabled methodConfigNameTextQuery
      //await thenClick(openLaunchAnalysisModalButtonQuery)
      click on (await enabled openLaunchAnalysisModalButtonQuery)
      new LaunchAnalysisModal
    }

    def openEditMode() = {
      click on (await enabled openEditModeQuery)
    }

    def changeMethodConfigName(newName: String) = {
      await enabled editMethodConfigNameInputQuery
      textField(editMethodConfigNameInputQuery).value = newName
    }

    def changeSnapshotId(newSnapshotId: Int) = {
      await enabled editMethodConfigSnapshotIdSelectQuery
      singleSel(editMethodConfigSnapshotIdSelectQuery).value = newSnapshotId.toString
    }

    def changeRootEntityType(newRootEntityType: String) = {
      await enabled editMethodConfigRootEntityTypeInputQuery
      singleSel(editMethodConfigRootEntityTypeInputQuery).value = newRootEntityType
    }

    def changeInputsOutputs(fields: Map[String, String]) = {
      for ((field, expression) <- fields) {
        val fieldInputQuery: Query = testId(field + "-text-input")
        await enabled fieldInputQuery
        textField(fieldInputQuery).value = expression
      }
    }

    def saveEdits() = {
      click on (await enabled saveEdittedMethodConfigButtonQuery)
    }

    def cancelEdits() = {
      click on (await enabled cancelEditMethodConfigModeButtonQuery)
    }

  }

}



/**
  * Page class for the launch analysis modal.
  */
class LaunchAnalysisModal(implicit webDriver: WebDriver) extends FireCloudView {

  /**
    *
    */
  def launchAnalysis(rootEntityType: String, entityId: String, expression: String = ""): Unit = { //Use Option(String) for expression?
    gestures.filterRootEntityType(rootEntityType)
    gestures.searchEntity(entityId)
    gestures.selectEntity(entityId)
    if (!expression.isEmpty()) { gestures.fillExpression(expression) }
    gestures.clicklaunchButton()
  }

  def filterRootEntityType(rootEntityType: String) = {
    gestures.filterRootEntityType(rootEntityType)
  }

  def searchAndSelectEntity(entityId: String) = {
    gestures.searchEntity(entityId)
    gestures.selectEntity(entityId)
  }

  def fillExpressionField(expression: String) = {
    gestures.fillExpression(expression)
  }

  def clickLaunchButton() = {
    gestures.clicklaunchButton()
  }

  def verifyNoDefaultEntityMessage(): Boolean = {
    gestures.isNoDefaultEntitiesMessagePresent()
  }

  def verifyWorkflowsWarning(): Boolean = {
    gestures.isNumberOfWorkflowWarningPresent()
  }

  def verifyWrongEntityError(errorText: String): Boolean = {
    gestures.isErrorTextPresent(errorText)
  }

  def closeModal() = {
    gestures.closeModal()
  }

  object gestures {
    private val participantRootEntityFilterButtonQuery: Query = testId("participant-filter-button")
    private val participantSetRootEntityFilterButtonQuery: Query = testId("participant_set-filter-button")
    private val entitySearchInputQuery: Query = testId("entity-table-search-input")
    private val expressionInputQuery: Query = testId("define-expression-input")
    private val emptyDefaultEntitiesMessageQuery: Query = testId("message-well")
    private val launchAnalysisButtonQuery: Query = testId("launch-button")
    private val closeModalXButtonQuery: Query = testId("x-button")
    private val numberOfWorkflowsWarningQuery: Query = testId("number-of-workflows-warning")
    //Need to add call-caching check-box

    private val emptyDefaultMessage = "There are no entities to display."

    def filterRootEntityType(rootEntityType: String) = {
      click on (await enabled testId(rootEntityType + "-filter-button"))
    }

    def filterParticipantSetType() = {
      click on (await enabled participantSetRootEntityFilterButtonQuery)
    }

    def searchEntity(entityId: String) = {
      await enabled entitySearchInputQuery
      searchField(entitySearchInputQuery).value = entityId
      pressKeys("\n")
    }

    def selectEntity(entityId: String) = {
      await enabled launchAnalysisButtonQuery
      click on (await enabled text(entityId))
    }

    def fillExpression(expression: String) = {
      await enabled expressionInputQuery
      searchField(expressionInputQuery).value = expression
    }

    def clicklaunchButton() = {
      click on (await enabled launchAnalysisButtonQuery)
    }

    def isNoDefaultEntitiesMessagePresent(): Boolean = {
      await enabled emptyDefaultEntitiesMessageQuery
      find(emptyDefaultEntitiesMessageQuery).size == 1
    }

    def closeModal() = {
      click on (await enabled closeModalXButtonQuery)
    }

    def isNumberOfWorkflowWarningPresent(): Boolean = {
      await enabled numberOfWorkflowsWarningQuery
      find(numberOfWorkflowsWarningQuery).size == 1
    }

    def isErrorTextPresent(errorText: String): Boolean = {
      val errorTextQuery: Query = text(errorText)
      await enabled errorTextQuery
      val error = find(errorTextQuery)
      error.size == 1
    }

  }

}




