/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.rfc8621.contract

import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic

import com.google.common.hash.Hashing
import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import jakarta.inject.Inject
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.{Domain, MailAddress, Username}
import org.apache.james.jmap.api.identity.{IdentityCreationRequest, IdentityHtmlSignatureUpdate, IdentityRepository, IdentityUpdateRequest}
import org.apache.james.jmap.api.model.{EmailAddress, EmailerName, HtmlSignature, Identity, IdentityId, IdentityName, TextSignature}
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UuidState.INSTANCE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.rrt.api.RecipientRewriteTable
import org.apache.james.rrt.lib.MappingSource
import org.apache.james.utils.{DataProbeImpl, GuiceProbe}
import org.junit.jupiter.api.{AfterEach, BeforeEach, Test}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

class IdentityProbeModule extends AbstractModule{
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[GuiceProbe])
      .addBinding()
      .to(classOf[IdentityProbe])
  }
}

class IdentityProbe @Inject()(identityRepository: IdentityRepository, recipientRewriteTable: RecipientRewriteTable) extends GuiceProbe {
  def save(user: Username, creationRequest: IdentityCreationRequest): Publisher[Identity] = identityRepository.save(user, creationRequest)
  def update(user: Username, identityId: IdentityId, identityUpdateRequest: IdentityUpdateRequest): SMono[Unit] = SMono(identityRepository.update(user, identityId, identityUpdateRequest))
  def removeDomainAliasMapping(aliasDomain: String, targetDomain: String): Unit =
    recipientRewriteTable.removeDomainAliasMapping(MappingSource.fromDomain(Domain.of(aliasDomain)), Domain.of(targetDomain))
}

object IdentityGetContract {
  case class TestContext(bobUsername: Username, bobLocalPart: String, bobAccountId: String, bobAliasLocalPart: String, domainAlias: String, defaultIdentityId: String)

  val currentContext: atomic.AtomicReference[TestContext] = new atomic.AtomicReference[TestContext]()
}

trait IdentityGetContract {
  import IdentityGetContract.{TestContext, currentContext}

  def bobUsername: Username = currentContext.get().bobUsername
  def bobLocalPart: String = currentContext.get().bobLocalPart
  def bobAccountId: String = currentContext.get().bobAccountId
  def bobAliasLocalPart: String = currentContext.get().bobAliasLocalPart
  def domainAlias: String = currentContext.get().domainAlias
  def defaultIdentityId: String = currentContext.get().defaultIdentityId
  def bobAliasIdentityId: String = UUID.nameUUIDFromBytes(s"$bobAliasLocalPart@${DOMAIN.asString}".getBytes(StandardCharsets.UTF_8)).toString
  def bobDomainAliasIdentityId: String = UUID.nameUUIDFromBytes(s"$bobLocalPart@$domainAlias".getBytes(StandardCharsets.UTF_8)).toString
  def bobAliasDomainAliasIdentityId: String = UUID.nameUUIDFromBytes(s"$bobAliasLocalPart@$domainAlias".getBytes(StandardCharsets.UTF_8)).toString

  private def accountId(username: Username): String =
    Hashing.sha256().hashString(username.asString(), StandardCharsets.UTF_8).toString

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    val uniqueSuffix = UUID.randomUUID().toString.replace("-", "").take(8)
    val bob = Username.fromLocalPartWithDomain(s"bob$uniqueSuffix", DOMAIN)
    val domainAlias = s"domain-alias-$uniqueSuffix.tld"
    currentContext.set(TestContext(
      bobUsername = bob,
      bobLocalPart = s"bob$uniqueSuffix",
      bobAccountId = accountId(bob),
      bobAliasLocalPart = s"bob-alias-$uniqueSuffix",
      domainAlias = domainAlias,
      defaultIdentityId = UUID.nameUUIDFromBytes(bob.asString.getBytes(StandardCharsets.UTF_8)).toString))

    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addDomain(domainAlias)
      .addUser(bob.asString, BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(bobUsername, BOB_PASSWORD)))
      .build
  }

  @AfterEach
  def tearDown(server: GuiceJamesServer): Unit = {
    val context = currentContext.get()
    server.getProbe(classOf[DataProbeImpl]).removeUserAliasMapping(context.bobAliasLocalPart, DOMAIN.asString, context.bobUsername.asString)
    server.getProbe(classOf[IdentityProbe]).removeDomainAliasMapping(context.domainAlias, DOMAIN.asString)
  }

  @Test
  def getIdentityShouldReturnDefaultIdentity(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Identity/get",
         |    {
         |      "accountId": "$bobAccountId",
         |      "ids": null
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .inPath("methodResponses[0][1]")
      .isEqualTo(
      s"""{
        |  "accountId": "$bobAccountId",
        |  "state": "${INSTANCE.value}",
        |  "list": [
        |      {
        |          "id": "$defaultIdentityId",
        |          "name": "${bobUsername.asString}",
        |          "email": "${bobUsername.asString}",
        |          "mayDelete": false,
        |          "textSignature":"",
        |          "htmlSignature":""
        |      }
        |  ]
        |}""".stripMargin)
  }

  @Test
  def getIdentityShouldReturnCustomIdentity(server: GuiceJamesServer): Unit = {
    val id = SMono(server.getProbe(classOf[IdentityProbe])
      .save(bobUsername, IdentityCreationRequest(name = Some(IdentityName("Bob (custom address)")),
        email = bobUsername.asMailAddress(),
        replyTo = Some(List(EmailAddress(Some(EmailerName("My Boss")), new MailAddress("boss@domain.tld")))),
        bcc = Some(List(EmailAddress(Some(EmailerName("My Boss 2")), new MailAddress("boss2@domain.tld")))),
        textSignature = Some(TextSignature("text signature")),
        htmlSignature = Some(HtmlSignature("html signature")))))
      .block()
      .id.id.toString

    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Identity/get",
         |    {
         |      "accountId": "$bobAccountId",
         |      "ids": ["$id"]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .inPath("methodResponses[0][1].list[0]")
      .isEqualTo(
      s"""{
         |	"bcc": [{
         |		"email": "boss2@domain.tld",
         |		"name": "My Boss 2"
         |	}],
         |	"email": "${bobUsername.asString}",
         |	"htmlSignature": "html signature",
         |	"id": "$id",
         |	"mayDelete": true,
         |	"name": "Bob (custom address)",
         |	"replyTo": [{
         |		"email": "boss@domain.tld",
         |		"name": "My Boss"
         |	}],
         |	"textSignature": "text signature"
         |}""".stripMargin)
  }

  @Test
  def getIdentityShouldReturnAliases(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl]).addUserAliasMapping(bobAliasLocalPart, "domain.tld", bobUsername.asString)
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Identity/get",
         |    {
         |      "accountId": "$bobAccountId",
         |      "ids": null
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .inPath("methodResponses[0][1]")
      .isEqualTo(
      s"""{
        |    "accountId": "$bobAccountId",
        |    "state": "${INSTANCE.value}",
        |    "list": [
        |        {
        |            "id": "$defaultIdentityId",
        |            "name": "${bobUsername.asString}",
        |            "email": "${bobUsername.asString}",
        |            "mayDelete": false,
        |            "textSignature":"",
        |            "htmlSignature":""
        |        },
        |        {
        |            "id": "$bobAliasIdentityId",
        |            "name": "${bobAliasLocalPart}@domain.tld",
        |            "email": "${bobAliasLocalPart}@domain.tld",
        |            "mayDelete": false,
        |            "textSignature":"",
        |            "htmlSignature":""
        |        }
        |    ]
        |}""".stripMargin)
  }

  @Test
  def getIdentityShouldSupportIdsField(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl]).addUserAliasMapping(bobAliasLocalPart, "domain.tld", bobUsername.asString)
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Identity/get",
         |    {
         |      "accountId": "$bobAccountId",
         |      "ids": ["idNotFound", "$bobAliasIdentityId"]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .inPath("methodResponses[0][1]")
      .isEqualTo(
      s"""{
        |    "accountId": "$bobAccountId",
        |    "state": "${INSTANCE.value}",
        |    "list": [
        |        {
        |            "id": "$bobAliasIdentityId",
        |            "name": "${bobAliasLocalPart}@domain.tld",
        |            "email": "${bobAliasLocalPart}@domain.tld",
        |            "mayDelete": false,
        |            "textSignature":"",
        |            "htmlSignature":""
        |        }
        |    ],
        |    "notFound": ["idNotFound"]
        |}""".stripMargin)
  }

  @Test
  def getIdentityShouldReturnDomainAliases(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl]).addUserAliasMapping(bobAliasLocalPart, "domain.tld", bobUsername.asString)
    server.getProbe(classOf[DataProbeImpl]).addDomainAliasMapping(domainAlias, "domain.tld")
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Identity/get",
         |    {
         |      "accountId": "$bobAccountId",
         |      "ids": null
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .inPath("methodResponses[0][1]")
      .isEqualTo(
      s"""{
        |    "accountId": "$bobAccountId",
        |    "state": "${INSTANCE.value}",
        |    "list": [
        |        {
        |            "id": "$defaultIdentityId",
        |            "name": "${bobUsername.asString}",
        |            "email": "${bobUsername.asString}",
        |            "mayDelete": false,
        |            "textSignature":"",
        |            "htmlSignature":""
        |        },
        |        {
        |            "id": "$bobDomainAliasIdentityId",
        |            "name": "${bobLocalPart}@$domainAlias",
        |            "email": "${bobLocalPart}@$domainAlias",
        |            "mayDelete": false,
        |            "textSignature":"",
        |            "htmlSignature":""
        |        },
        |        {
        |            "id": "$bobAliasIdentityId",
        |            "name": "${bobAliasLocalPart}@domain.tld",
        |            "email": "${bobAliasLocalPart}@domain.tld",
        |            "mayDelete": false,
        |            "textSignature":"",
        |            "htmlSignature":""
        |        },
        |        {
        |            "id": "$bobAliasDomainAliasIdentityId",
        |            "name": "${bobAliasLocalPart}@$domainAlias",
        |            "email": "${bobAliasLocalPart}@$domainAlias",
        |            "mayDelete": false,
        |            "textSignature":"",
        |            "htmlSignature":""
        |        }
        |    ]
        |}""".stripMargin)
  }

  @Test
  def getIdentityShouldReturnSortOrder(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl]).addUserAliasMapping(bobAliasLocalPart, "domain.tld", bobUsername.asString)
    server.getProbe(classOf[DataProbeImpl]).addDomainAliasMapping(domainAlias, "domain.tld")
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission", "urn:apache:james:params:jmap:mail:identity:sortorder"],
         |  "methodCalls": [[
         |    "Identity/get",
         |    {
         |      "accountId": "$bobAccountId",
         |      "ids": null
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .withOptions(IGNORING_ARRAY_ORDER)
      .inPath("methodResponses[0][1]")
      .isEqualTo(
      s"""{
        |    "accountId": "$bobAccountId",
        |    "state": "${INSTANCE.value}",
        |    "list": [
        |        {
        |            "id": "$defaultIdentityId",
        |            "name": "${bobUsername.asString}",
        |            "email": "${bobUsername.asString}",
        |            "mayDelete": false,
        |            "textSignature":"",
        |            "htmlSignature":"",
        |            "sortOrder": 100
        |        },
        |        {
        |            "id": "$bobDomainAliasIdentityId",
        |            "name": "${bobLocalPart}@$domainAlias",
        |            "email": "${bobLocalPart}@$domainAlias",
        |            "mayDelete": false,
        |            "textSignature":"",
        |            "htmlSignature":"",
        |            "sortOrder": 100
        |        },
        |        {
        |            "id": "$bobAliasIdentityId",
        |            "name": "${bobAliasLocalPart}@domain.tld",
        |            "email": "${bobAliasLocalPart}@domain.tld",
        |            "mayDelete": false,
        |            "textSignature":"",
        |            "htmlSignature":"",
        |            "sortOrder": 100
        |        },
        |        {
        |            "id": "$bobAliasDomainAliasIdentityId",
        |            "name": "${bobAliasLocalPart}@$domainAlias",
        |            "email": "${bobAliasLocalPart}@$domainAlias",
        |            "mayDelete": false,
        |            "textSignature":"",
        |            "htmlSignature":"",
        |            "sortOrder": 100
        |        }
        |    ]
        |}""".stripMargin)
  }

  @Test
  def propertiesShouldBeSupported(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Identity/get",
         |    {
         |      "accountId": "$bobAccountId",
         |      "ids": null,
         |      "properties": ["id", "name", "email", "replyTo", "bcc", "textSignature", "htmlSignature", "mayDelete"]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString


    assertThatJson(response)
      .inPath("methodResponses[0][1]")
      .isEqualTo(
        s"""{
          |  "accountId": "$bobAccountId",
          |  "state": "${INSTANCE.value}",
          |  "list": [
          |      {
          |          "id": "$defaultIdentityId",
          |          "name": "${bobUsername.asString}",
          |          "email": "${bobUsername.asString}",
          |          "mayDelete": false,
          |          "textSignature":"",
          |          "htmlSignature":""
          |      }
          |  ]
          |}""".stripMargin)
  }

  @Test
  def propertiesShouldBeFiltered(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Identity/get",
         |    {
         |      "accountId": "$bobAccountId",
         |      "ids": null,
         |      "properties": ["id", "email"]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString


    assertThatJson(response)
      .inPath("methodResponses[0][1]")
      .isEqualTo(
        s"""{
          |  "accountId": "$bobAccountId",
          |  "state": "${INSTANCE.value}",
          |  "list": [
          |      {
          |          "id": "$defaultIdentityId",
          |          "email": "${bobUsername.asString}"
          |      }
          |  ]
          |}""".stripMargin)
  }

  @Test
  def badPropertiesShouldBeRejected(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Identity/get",
         |    {
         |      "accountId": "$bobAccountId",
         |      "ids": null,
         |      "properties": ["id", "bad"]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString


    assertThatJson(response)
      .isEqualTo(
        s"""{
          |    "sessionState": "${SESSION_STATE.value}",
          |    "methodResponses": [
          |        [
          |            "error",
          |            {
          |                "type": "invalidArguments",
          |                "description": "The following properties [bad] do not exist."
          |            },
          |            "c1"
          |        ]
          |    ]
          |}""".stripMargin)
  }

  @Test
  def badAccountIdShouldBeRejected(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Identity/get",
         |    {
         |      "accountId": "bad",
         |      "ids": null
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
          |    "sessionState": "${SESSION_STATE.value}",
          |    "methodResponses": [
          |        [
          |            "error",
          |            {
          |                "type": "accountNotFound"
          |            },
          |            "c1"
          |        ]
          |    ]
          |}""".stripMargin)
  }

  @Test
  def getRandomIdShouldNotFail(): Unit = {
    val id = UUID.randomUUID().toString
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Identity/get",
         |    {
         |      "accountId": "$bobAccountId",
         |      "ids": ["$id"]
         |    },
         |    "c1"]]
         |}""".stripMargin

    val response =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |	"sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"methodResponses": [
           |		[
           |			"Identity/get",
           |			{
           |				"accountId": "$bobAccountId",
           |				"notFound": [
           |					"$id"
           |				],
           |				"state": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |				"list": []
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
  }

  @Test
  def mayDeleteShouldReturnFalseWhenUpdateServerSetIdentity(server: GuiceJamesServer): Unit = {
    val serverSetIdentityId: String =  `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body( s"""{
                |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
                |  "methodCalls": [[
                |    "Identity/get",
                |    {
                |      "accountId": "$bobAccountId",
                |      "ids": null
                |    },
                |    "c1"]]
                |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .jsonPath()
      .get("methodResponses[0][1].list[0].id")

    // When admin update the server set identity
    server.getProbe(classOf[IdentityProbe])
      .update(bobUsername, IdentityId(UUID.fromString(serverSetIdentityId)), IdentityUpdateRequest(htmlSignature = Some(IdentityHtmlSignatureUpdate(HtmlSignature("html signature")))))
      .block()

    // Then mayDelete always return false
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body( s"""{
                |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
                |  "methodCalls": [[
                |    "Identity/get",
                |    {
                |      "accountId": "$bobAccountId",
                |      "ids": [ "$serverSetIdentityId" ],
                |      "properties": ["id", "mayDelete"]
                |    },
                |    "c1"]]
                |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response)
      .inPath("methodResponses[0][1].list[0]")
      .isEqualTo(
        s"""{
           |    "id": "$serverSetIdentityId",
           |    "mayDelete": false
           |}""".stripMargin)
  }
}
