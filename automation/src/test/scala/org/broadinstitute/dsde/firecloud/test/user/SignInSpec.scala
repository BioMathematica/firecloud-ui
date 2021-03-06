package org.broadinstitute.dsde.firecloud.test.user

import org.broadinstitute.dsde.firecloud.fixture.UserFixtures
import org.broadinstitute.dsde.firecloud.page.user.SignInPage
import org.broadinstitute.dsde.firecloud.FireCloudConfig.{FireCloud, Users}
import org.broadinstitute.dsde.workbench.fixture.{FailedTestRetryable, TestReporterFixture}
import org.broadinstitute.dsde.workbench.service.test.WebBrowserSpec
import org.broadinstitute.dsde.workbench.service.util.Tags
import org.scalatest._
import org.scalatest.tagobjects.Retryable
import org.scalatest.time.{Millis, Seconds, Span}


class SignInSpec extends FreeSpec with WebBrowserSpec with FailedTestRetryable with UserFixtures with Matchers with TestReporterFixture {

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(500, Millis)))


  /**
    * ignoring the test. manual SignIn testing is required in release checklist.
    * https://github.com/broadinstitute/firecloud-develop/blob/dev/release-tasks-template.md
    */
  "A user with a registered account" - {

    "should be able to log in and out as different users in same browser" taggedAs (Retryable, Tags.SignInRealTest) ignore {
      val user1 = Users.Students.getUserCredential("harry")
      withWebDriver { implicit driver =>
        withSignInReal(user1) { listPage =>
          eventually { listPage.readUserEmail() shouldEqual user1.email }
        }
        // making sure Sign Out worked
        eventually { new SignInPage(FireCloud.baseUrl).isOpen shouldEqual true }

        // should be able to log in in same web browser as a different user
        val user2 = Users.Students.getUserCredential("ron")
        withSignInReal(user2) { listPage =>
          eventually {
            listPage.readUserEmail() shouldEqual user2.email
          }
        }
        eventually { new SignInPage(FireCloud.baseUrl).isOpen shouldEqual true }
      }
    }

  }

}
