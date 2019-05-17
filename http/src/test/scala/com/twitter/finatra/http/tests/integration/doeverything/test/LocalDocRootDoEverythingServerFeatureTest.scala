package com.twitter.finatra.http.tests.integration.doeverything.test

import com.twitter.finagle.http.Status._
import com.twitter.finatra.http.EmbeddedHttpServer
import com.twitter.finatra.http.integration.doeverything.test.DocRootLocalFilesystemTestUtility
import com.twitter.finatra.http.marshalling.mustache.MustacheService
import com.twitter.finatra.http.tests.integration.doeverything.main.DoEverythingServer
import com.twitter.finatra.http.tests.integration.doeverything.main.domain.TestUserView
import com.twitter.finatra.test.LocalFilesystemTestUtils._
import com.twitter.inject.server.FeatureTest
import java.io.{File, FileWriter}

class LocalDocRootDoEverythingServerFeatureTest
    extends FeatureTest
    with DocRootLocalFilesystemTestUtility {

  override protected def beforeAll() = {
    super.beforeAll()

    // create src/main/resources/templates directory and add files
    val templates = createFile(s"${BaseDirectory}src/main/resources/templates")
    writeStringToFile(createFile(templates, "testuser.mustache"), testUserMustacheString)
    writeStringToFile(
      createFile(templates, "testuser2.mustache"),
      testUser2MustacheString
    )
    writeStringToFile(createFile(templates, "testHtml.mustache"), testHtmlMustacheString)

    // create src/main/webapp directory and add files
    val webapp = createFile(s"${BaseDirectory}src/main/webapp")
    writeStringToFile(createFile(webapp, "testfile.txt"), testFileText)
    writeStringToFile(createFile(webapp, "testindex.html"), testIndexHtml)
  }

  override protected def afterAll() = {
    // try to help clean up
    new File(s"${BaseDirectory}src").delete
    super.afterAll()
  }

  override val server = new EmbeddedHttpServer(
    flags = Map(
      "local.doc.root" -> s"${BaseDirectory}src/main/webapp",
      "mustache.templates.dir" -> s"${BaseDirectory}src/main/resources/templates"
    ),
    args = Array("-magicNum=1", "-moduleMagicNum=2"),
    twitterServer = new DoEverythingServer
  )

  test("DoEverythingServer#getView") {
    server.httpGet(
      "/getView?age=18&name=bob",
      andExpect = Ok,
      withBody = "age:18\nname:bob\nuser1\nuser2\n"
    )
  }

  test("DoEverythingServer#formPostViewFromBuilderViewWithDiffTemplateThanAnnotation") {
    server.httpFormPost(
      "/formPostViewFromBuilderView",
      params = Map("name" -> "bob", "age" -> "18"),
      andExpect = Ok,
      withBody = "age2:18\nname2:bob\nuser1\nuser2\n"
    )
  }

  test("DoEverythingServer#formPostViewFromBuilderHtml") {
    server.httpFormPost(
      "/formPostViewFromBuilderHtml",
      params = Map("name" -> "bob", "age" -> "18"),
      andExpect = Ok,
      withBody = "age:18\nname:bob\nuser1\nuser2\n"
    )
  }

  test("DoEverythingServer#formPostViewFromBuilderCreatedView") {
    val response = server.httpFormPost(
      "/formPostViewFromBuilderCreatedView",
      params = Map("name" -> "bob", "age" -> "18"),
      andExpect = Created,
      withBody = "age2:18\nname2:bob\nuser1\nuser2\n"
    )

    response.location should equal(Some(server.fullHttpURI("/foo/1")))
  }

  test("DoEverythingServer#formPostViewFromBuilderCreatedHtml") {
    val response = server.httpFormPost(
      "/formPostViewFromBuilderCreatedHtml",
      params = Map("name" -> "bob", "age" -> "18"),
      andExpect = Created,
      withBody = "age:18\nname:bob\nuser1\nuser2\n"
    )

    response.location should equal(Some(server.fullHttpURI("/foo/1")))
  }

  test("DoEverythingServer#testfile") {
    server.httpGet("/testfile", andExpect = Ok, withBody = "testfile123")
  }

  test("DoEverythingServer#testfile when not found") {
    server.httpGet(
      "/testfileWhenNotfound",
      andExpect = NotFound,
      withBody = "/doesntexist.txt not found"
    )
  }

  test("DoEverythingServer#index root") {
    server.httpGet("/index/", andExpect = Ok, withBody = "testindex")
  }

  test("DoEverythingServer#index file without extension") {
    server.httpGet("/index/testfile", andExpect = Ok, withBody = "testindex")
  }

  test("DoEverythingServer#index file with extension") {
    server.httpGet("/index/testfile.txt", andExpect = Ok, withBody = "testfile123")
  }

  test("DoEverythingServer#TestCaseClassWithHtml") {
    server.httpGet(
      "/testClassWithHtml",
      andExpect = Ok,
      withJsonBody =
        """
          |{
          |  "address" : "123 Main St. Anywhere, CA US 90210",
          |  "phone" : "+12221234567",
          |  "rendered_html" : "&lt;div class=&quot;nav&quot;&gt;\n  &lt;table cellpadding=&quot;0&quot; cellspacing=&quot;0&quot;&gt;\n    &lt;tr&gt;\n        &lt;th&gt;Name&lt;/th&gt;\n        &lt;th&gt;Age&lt;/th&gt;\n        &lt;th&gt;Friends&lt;/th&gt;\n    &lt;/tr&gt;\n    &lt;tr&gt;\n        &lt;td&gt;age2:28&lt;/td&gt;\n        &lt;td&gt;name:Bob Smith&lt;/td&gt;\n        &lt;td&gt;\n            user1\n            user2\n        &lt;/td&gt;\n    &lt;/tr&gt;\n  &lt;/table&gt;\n&lt;/div&gt;"
          |}
        """.stripMargin
    )
  }

  test("DoEverythingServer#Support un-cached templates") {
    val testUser = TestUserView(28, "Bob Smith", Seq("user1", "user2"))

    val mustacheService = injector.instance[MustacheService]
    val firstResult = mustacheService.createString("testuser.mustache", testUser)
    firstResult should be("age:28\nname:Bob Smith\nuser1\nuser2\n")

    // alter the file
    val testUserMustacheFile =
      new FileWriter(s"${BaseDirectory}src/main/resources/templates/testuser.mustache")
    testUserMustacheFile.write("")
    testUserMustacheFile.append(
      "another age:{{age}}\nanother name:{{name}}\n{{#friends}}\n{{.}}\n{{/friends}}"
    )
    testUserMustacheFile.close()

    val alteredResult = mustacheService.createString("testuser.mustache", testUser)
    alteredResult should be("another age:28\nanother name:Bob Smith\nuser1\nuser2\n")
  }
}
