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

import java.util.UUID

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import jakarta.inject.Inject
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import net.javacrumbs.jsonunit.core.internal.Options
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.jmap.api.identity.{IdentityCreationRequest, IdentityHtmlSignatureUpdate, IdentityRepository, IdentityUpdateRequest}
import org.apache.james.jmap.api.model.{EmailAddress, EmailerName, HtmlSignature, Identity, IdentityId, IdentityName, TextSignature}
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UuidState.INSTANCE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.utils.{DataProbeImpl, GuiceProbe}
import org.junit.jupiter.api.{BeforeEach, Test}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

class IdentityProbeModule extends AbstractModule{
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[GuiceProbe])
      .addBinding()
      .to(classOf[IdentityProbe])
  }
}

class IdentityProbe @Inject()(identityRepository: IdentityRepository) extends GuiceProbe {
  def save(user: Username, creationRequest: IdentityCreationRequest): Publisher[Identity] = identityRepository.save(user, creationRequest)
  def update(user: Username, identityId: IdentityId, identityUpdateRequest: IdentityUpdateRequest): SMono[Unit] = SMono(identityRepository.update(user, identityId, identityUpdateRequest))
}

trait IdentityGetContract {
  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addDomain("domain-alias.tld")
      .addUser(BOB.asString, BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  @Test
  def getIdentityShouldReturnDefaultIdentity(): Unit = {
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Identity/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
        |  "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |  "state": "${INSTANCE.value}",
        |  "list": [
        |      {
        |          "id": "becaf930-ea9e-3ef4-81ea-206eecb04aa7",
        |          "name": "bob@domain.tld",
        |          "email": "bob@domain.tld",
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
      .save(BOB, IdentityCreationRequest(name = Some(IdentityName("Bob (custom address)")),
        email = BOB.asMailAddress(),
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
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
         |	"email": "bob@domain.tld",
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
    server.getProbe(classOf[DataProbeImpl]).addUserAliasMapping("bob-alias", "domain.tld", "bob@domain.tld")
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Identity/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .inPath("methodResponses[0][1]")
      .isEqualTo(
      s"""{
        |    "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |    "state": "${INSTANCE.value}",
        |    "list": [
        |        {
        |            "id": "becaf930-ea9e-3ef4-81ea-206eecb04aa7",
        |            "name": "bob@domain.tld",
        |            "email": "bob@domain.tld",
        |            "mayDelete": false,
        |            "textSignature":"",
        |            "htmlSignature":""
        |        },
        |        {
        |            "id": "3739a34e-cd8c-3a42-bf28-578ba24da9da",
        |            "name": "bob-alias@domain.tld",
        |            "email": "bob-alias@domain.tld",
        |            "mayDelete": false,
        |            "textSignature":"",
        |            "htmlSignature":""
        |        }
        |    ]
        |}""".stripMargin)
  }

  @Test
  def getIdentityShouldSupportIdsField(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl]).addUserAliasMapping("bob-alias", "domain.tld", "bob@domain.tld")
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Identity/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |      "ids": ["idNotFound", "3739a34e-cd8c-3a42-bf28-578ba24da9da"]
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
        |    "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |    "state": "${INSTANCE.value}",
        |    "list": [
        |        {
        |            "id": "3739a34e-cd8c-3a42-bf28-578ba24da9da",
        |            "name": "bob-alias@domain.tld",
        |            "email": "bob-alias@domain.tld",
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
    server.getProbe(classOf[DataProbeImpl]).addUserAliasMapping("bob-alias", "domain.tld", "bob@domain.tld")
    server.getProbe(classOf[DataProbeImpl]).addDomainAliasMapping("domain-alias.tld", "domain.tld")
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |  "methodCalls": [[
         |    "Identity/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .inPath("methodResponses[0][1]")
      .isEqualTo(
      s"""{
        |    "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |    "state": "${INSTANCE.value}",
        |    "list": [
        |        {
        |            "id": "becaf930-ea9e-3ef4-81ea-206eecb04aa7",
        |            "name": "bob@domain.tld",
        |            "email": "bob@domain.tld",
        |            "mayDelete": false,
        |            "textSignature":"",
        |            "htmlSignature":""
        |        },
        |        {
        |            "id": "b025b9f1-95c6-30fb-a9d4-0fddfcc3a92c",
        |            "name": "bob@domain-alias.tld",
        |            "email": "bob@domain-alias.tld",
        |            "mayDelete": false,
        |            "textSignature":"",
        |            "htmlSignature":""
        |        },
        |        {
        |            "id": "3739a34e-cd8c-3a42-bf28-578ba24da9da",
        |            "name": "bob-alias@domain.tld",
        |            "email": "bob-alias@domain.tld",
        |            "mayDelete": false,
        |            "textSignature":"",
        |            "htmlSignature":""
        |        },
        |        {
        |            "id": "d2e1e9d2-78ef-3967-87c6-cdc2e0f1541d",
        |            "name": "bob-alias@domain-alias.tld",
        |            "email": "bob-alias@domain-alias.tld",
        |            "mayDelete": false,
        |            "textSignature":"",
        |            "htmlSignature":""
        |        }
        |    ]
        |}""".stripMargin)
  }

  @Test
  def getIdentityShouldReturnSortOrder(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl]).addUserAliasMapping("bob-alias", "domain.tld", "bob@domain.tld")
    server.getProbe(classOf[DataProbeImpl]).addDomainAliasMapping("domain-alias.tld", "domain.tld")
    val request =
      s"""{
         |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission", "urn:apache:james:params:jmap:mail:identity:sortorder"],
         |  "methodCalls": [[
         |    "Identity/get",
         |    {
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
      .withOptions(new Options(IGNORING_ARRAY_ORDER))
      .inPath("methodResponses[0][1]")
      .isEqualTo(
      s"""{
        |    "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |    "state": "${INSTANCE.value}",
        |    "list": [
        |        {
        |            "id": "becaf930-ea9e-3ef4-81ea-206eecb04aa7",
        |            "name": "bob@domain.tld",
        |            "email": "bob@domain.tld",
        |            "mayDelete": false,
        |            "textSignature":"",
        |            "htmlSignature":"",
        |            "sortOrder": 100
        |        },
        |        {
        |            "id": "b025b9f1-95c6-30fb-a9d4-0fddfcc3a92c",
        |            "name": "bob@domain-alias.tld",
        |            "email": "bob@domain-alias.tld",
        |            "mayDelete": false,
        |            "textSignature":"",
        |            "htmlSignature":"",
        |            "sortOrder": 100
        |        },
        |        {
        |            "id": "3739a34e-cd8c-3a42-bf28-578ba24da9da",
        |            "name": "bob-alias@domain.tld",
        |            "email": "bob-alias@domain.tld",
        |            "mayDelete": false,
        |            "textSignature":"",
        |            "htmlSignature":"",
        |            "sortOrder": 100
        |        },
        |        {
        |            "id": "d2e1e9d2-78ef-3967-87c6-cdc2e0f1541d",
        |            "name": "bob-alias@domain-alias.tld",
        |            "email": "bob-alias@domain-alias.tld",
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
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
          |  "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
          |  "state": "${INSTANCE.value}",
          |  "list": [
          |      {
          |          "id": "becaf930-ea9e-3ef4-81ea-206eecb04aa7",
          |          "name": "bob@domain.tld",
          |          "email": "bob@domain.tld",
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
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
          |  "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
          |  "state": "${INSTANCE.value}",
          |  "list": [
          |      {
          |          "id": "becaf930-ea9e-3ef4-81ea-206eecb04aa7",
          |          "email": "bob@domain.tld"
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
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
         |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
                |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
      .update(BOB, IdentityId(UUID.fromString(serverSetIdentityId)), IdentityUpdateRequest(htmlSignature = Some(IdentityHtmlSignatureUpdate(HtmlSignature("html signature")))))
      .block()

    // Then mayDelete always return false
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body( s"""{
                |  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
                |  "methodCalls": [[
                |    "Identity/get",
                |    {
                |      "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
