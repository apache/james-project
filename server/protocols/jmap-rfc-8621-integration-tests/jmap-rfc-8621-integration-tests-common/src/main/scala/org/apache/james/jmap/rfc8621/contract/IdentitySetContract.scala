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
import java.util.concurrent.atomic.AtomicReference

import com.google.common.hash.Hashing
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.jmap.api.identity.IdentityCreationRequest
import org.apache.james.jmap.api.model.{EmailAddress, EmailerName, HtmlSignature, IdentityName, TextSignature}
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.UuidState.INSTANCE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.IdentitySetContract.TestContext
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Test}
import reactor.core.scala.publisher.SMono

object IdentitySetContract {
  case class TestContext(bobUsername: Username, bobAccountId: String, bobAliasLocalPart: String)
  val currentContext: AtomicReference[TestContext] = new AtomicReference[TestContext]()

  val IDENTITY_CREATION_REQUEST: IdentityCreationRequest = IdentityCreationRequest(name = Some(IdentityName("Bob (custom address)")),
    email = BOB.asMailAddress(),
    replyTo = Some(List(EmailAddress(Some(EmailerName("My Boss")), new MailAddress("boss@domain.tld")))),
    bcc = Some(List(EmailAddress(Some(EmailerName("My Boss 2")), new MailAddress("boss2@domain.tld")))),
    textSignature = Some(TextSignature("text signature")),
    htmlSignature = Some(HtmlSignature("html signature")))
}
trait IdentitySetContract {
  def bobUsername: Username = IdentitySetContract.currentContext.get().bobUsername
  def bobAccountId: String = IdentitySetContract.currentContext.get().bobAccountId
  def bobAliasLocalPart: String = IdentitySetContract.currentContext.get().bobAliasLocalPart
  def bobAliasAddress: String = s"$bobAliasLocalPart@${DOMAIN.asString}"
  def identityCreationRequest: IdentityCreationRequest = IdentityCreationRequest(name = Some(IdentityName("Bob (custom address)")),
    email = bobUsername.asMailAddress(),
    replyTo = Some(List(EmailAddress(Some(EmailerName("My Boss")), new MailAddress("boss@domain.tld")))),
    bcc = Some(List(EmailAddress(Some(EmailerName("My Boss 2")), new MailAddress("boss2@domain.tld")))),
    textSignature = Some(TextSignature("text signature")),
    htmlSignature = Some(HtmlSignature("html signature")))

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    val uniqueSuffix = UUID.randomUUID().toString.replace("-", "").take(8)
    val bob = Username.fromLocalPartWithDomain(s"bob$uniqueSuffix", DOMAIN)
    IdentitySetContract.currentContext.set(TestContext(
      bobUsername = bob,
      bobAccountId = Hashing.sha256().hashString(bob.asString(), StandardCharsets.UTF_8).toString,
      bobAliasLocalPart = s"bob-alias-$uniqueSuffix"))

    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addDomain("domain-alias.tld")
      .addUser(bob.asString, BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(bob, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build
  }

  @Test
  def setIdentityShouldSucceed(): Unit = {
    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		[
         |			"Identity/set",
         |			{
         |				"accountId": "$bobAccountId",
         |				"create": {
         |					"4f29": {
         |						"name": "Bob",
         |						"email": "${bobUsername.asString}",
         |						"replyTo": [{
         |							"name": "Alice",
         |							"email": "alice@domain.tld"
         |						}],
         |						"bcc": [{
         |							"name": "David",
         |							"email": "david@domain.tld"
         |						}],
         |						"textSignature": "Some text signature",
         |						"htmlSignature": "<p>Some html signature</p>"
         |					}
         |				}
         |			},
         |			"c1"
         |		],
         |		["Identity/get",
         |			{
         |				"accountId": "$bobAccountId",
         |				"ids": null
         |			}, "c2"
         |		]
         |
         |	]
         |}""".stripMargin

    val response =  `given`
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
      .when(net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER)
      .isEqualTo(
        s"""{
           |	"sessionState": "${SESSION_STATE.value}",
           |	"methodResponses": [
           |		[
           |			"Identity/set",
           |			{
           |				"accountId": "$bobAccountId",
           |				"newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |				"created": {
           |					"4f29": {
           |						"id": "$${json-unit.ignore}",
           |						"mayDelete": true
           |					}
           |				}
           |			},
           |			"c1"
           |		],
           |		[
           |			"Identity/get",
           |			{
           |				"accountId": "$bobAccountId",
           |				"state": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |				"list": [{
           |						"id": "$${json-unit.ignore}",
           |						"name": "Bob",
           |						"email": "${bobUsername.asString}",
           |						"replyTo": [{
           |							"name": "Alice",
           |							"email": "alice@domain.tld"
           |						}],
           |						"bcc": [{
           |							"name": "David",
           |							"email": "david@domain.tld"
           |						}],
           |						"textSignature": "Some text signature",
           |						"htmlSignature": "<p>Some html signature</p>",
           |						"mayDelete": true
           |					},
           |					{
           |						"id": "${UUID.nameUUIDFromBytes(bobUsername.asString.getBytes(StandardCharsets.UTF_8))}",
           |						"name": "${bobUsername.asString}",
           |						"email": "${bobUsername.asString}",
           |						"textSignature": "",
           |						"htmlSignature": "",
           |						"mayDelete": false
           |					}
           |				]
           |			},
           |			"c2"
           |		]
           |	]
           |}""".stripMargin)
  }

  @Test
  def setIdentityShouldSucceedWithSortOrder(): Unit = {
    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission", "urn:apache:james:params:jmap:mail:identity:sortorder"],
         |	"methodCalls": [
         |		[
         |			"Identity/set",
         |			{
         |				"accountId": "$bobAccountId",
         |				"create": {
         |					"4f29": {
         |						"name": "Bob",
         |						"email": "${bobUsername.asString}",
         |						"replyTo": [{
         |							"name": "Alice",
         |							"email": "alice@domain.tld"
         |						}],
         |						"bcc": [{
         |							"name": "David",
         |							"email": "david@domain.tld"
         |						}],
         |						"textSignature": "Some text signature",
         |						"htmlSignature": "<p>Some html signature</p>",
         |            "sortOrder":354
         |					}
         |				}
         |			},
         |			"c1"
         |		],
         |		["Identity/get",
         |			{
         |				"accountId": "$bobAccountId",
         |				"ids": ["#4f29"]
         |			}, "c2"
         |		]
         |
         |	]
         |}""".stripMargin

    val response =  `given`
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
      .when(net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER)
      .isEqualTo(
        s"""{
           |	"sessionState": "${SESSION_STATE.value}",
           |	"methodResponses": [
           |		[
           |			"Identity/set",
           |			{
           |				"accountId": "$bobAccountId",
           |				"newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |				"created": {
           |					"4f29": {
           |						"id": "$${json-unit.ignore}",
           |						"mayDelete": true
           |					}
           |				}
           |			},
           |			"c1"
           |		],
           |		[
           |			"Identity/get",
           |			{
           |				"accountId": "$bobAccountId",
           |				"state": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |				"list": [{
           |						"id": "$${json-unit.ignore}",
           |						"name": "Bob",
           |						"email": "${bobUsername.asString}",
           |						"replyTo": [{
           |							"name": "Alice",
           |							"email": "alice@domain.tld"
           |						}],
           |						"bcc": [{
           |							"name": "David",
           |							"email": "david@domain.tld"
           |						}],
           |						"textSignature": "Some text signature",
           |						"htmlSignature": "<p>Some html signature</p>",
           |						"mayDelete": true,
           |            "sortOrder": 354
           |					}
           |				]
           |			},
           |			"c2"
           |		]
           |	]
           |}""".stripMargin)
  }

  @Test
  def setIdentityAndGetIdentityCombinedShouldSucceed(): Unit = {
    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		[
         |			"Identity/set",
         |			{
         |				"accountId": "$bobAccountId",
         |				"create": {
         |					"4f29": {
         |						"name": "Bob",
         |						"email": "${bobUsername.asString}",
         |						"replyTo": [{
         |							"name": "Alice",
         |							"email": "alice@domain.tld"
         |						}],
         |						"bcc": [{
         |							"name": "David",
         |							"email": "david@domain.tld"
         |						}],
         |						"textSignature": "Some text signature",
         |						"htmlSignature": "<p>Some html signature</p>"
         |					}
         |				}
         |			},
         |			"c1"
         |		],
         |		["Identity/get",
         |			{
         |				"accountId": "$bobAccountId",
         |				"ids": ["#4f29"]
         |			}, "c2"
         |		]
         |
         |	]
         |}""".stripMargin

    val response =  `given`
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
           |			"Identity/set",
           |			{
           |				"accountId": "$bobAccountId",
           |				"newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |				"created": {
           |					"4f29": {
           |						"id": "$${json-unit.ignore}",
           |						"mayDelete": true
           |					}
           |				}
           |			},
           |			"c1"
           |		],
           |		[
           |			"Identity/get",
           |			{
           |				"accountId": "$bobAccountId",
           |				"state": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |				"list": [{
           |					"id": "$${json-unit.ignore}",
           |					"name": "Bob",
           |					"email": "${bobUsername.asString}",
           |					"replyTo": [{
           |						"name": "Alice",
           |						"email": "alice@domain.tld"
           |					}],
           |					"bcc": [{
           |						"name": "David",
           |						"email": "david@domain.tld"
           |					}],
           |					"textSignature": "Some text signature",
           |					"htmlSignature": "<p>Some html signature</p>",
           |					"mayDelete": true
           |				}]
           |			},
           |			"c2"
           |		]
           |	]
           |}""".stripMargin)
  }

  @Test
  def setIdentityWithSomePropertiesOmittedShouldSucceedAndReturnDefaultValues(): Unit = {
    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		[
         |			"Identity/set",
         |			{
         |				"accountId": "$bobAccountId",
         |				"create": {
         |					"4f29": {
         |						"email": "${bobUsername.asString}"
         |					}
         |				}
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin

    val response =  `given`
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
           |	"accountId": "$bobAccountId",
           |	"newState": "${INSTANCE.serialize}",
           |	"created": {
           |		"4f29": {
           |			"id": "$${json-unit.ignore}",
           |			"name": "",
           |			"textSignature": "",
           |			"htmlSignature": "",
           |			"mayDelete": true
           |		}
           |	}
           |}""".stripMargin)
  }

  @Test
  def setIdentityShouldCreatedSeveralValidCreationRequest(): Unit = {
    val request: String =
      s"""{
        |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
        |	"methodCalls": [
        |		[
        |			"Identity/set",
        |			{
        |				"accountId": "$bobAccountId",
        |				"create": {
        |					"4f28": {
        |						"name": "Identity1",
        |						"email": "${bobUsername.asString}",
        |						"replyTo": [{
        |							"name": "Alice",
        |							"email": "alice@domain.tld"
        |						}],
        |						"bcc": [{
        |							"name": "David",
        |							"email": "david@domain.tld"
        |						}],
        |						"textSignature": "Some text signature",
        |						"htmlSignature": "<p>Some html signature</p>"
        |					},
        |					"4f29": {
        |						"name": "Identity2",
        |						"email": "${bobUsername.asString}",
        |						"replyTo": null,
        |						"bcc": null,
        |						"textSignature": "Some text signature",
        |						"htmlSignature": "<p>Some html signature</p>"
        |					}
        |				}
        |			},
        |			"c1"
        |		]
        |	]
        |}""".stripMargin

    val response: String = `given`
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
           |	"accountId": "$bobAccountId",
           |	"newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"created": {
           |		"4f28": {
           |			"id": "$${json-unit.ignore}",
           |			"mayDelete": true
           |		},
           |		"4f29": {
           |			"id": "$${json-unit.ignore}",
           |			"mayDelete": true
           |		}
           |	}
           |}""".stripMargin)
  }

  @Test
  def setIdentityShouldReturnForbiddenFromErrorWhenForbiddenEmailProperty(): Unit = {
    val request: String =
      s"""{
        |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
        |	"methodCalls": [
        |		[
        |			"Identity/set",
        |			{
        |				"accountId": "$bobAccountId",
        |				"create": {
        |					"4f28": {
        |						"name": "valid send from identity",
        |						"email": "${bobUsername.asString}"
        |					},
        |					"4f29": {
        |						"name": "forbidden send from identity",
        |						"email": "${bobAliasAddress}"
        |					}
        |				}
        |			},
        |			"c1"
        |		]
        |	]
        |}""".stripMargin

    val response: String = `given`
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
           |	"accountId": "$bobAccountId",
           |	"newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"created": {
           |		"4f28": {
           |			"id": "$${json-unit.ignore}",
           |			"textSignature": "",
           |			"htmlSignature": "",
           |			"mayDelete": true
           |		}
           |	},
           |	"notCreated": {
           |		"4f29": {
           |			"type": "forbiddenFrom",
           |			"description": "Can not send from ${bobAliasAddress}"
           |		}
           |	}
           |}""".stripMargin)
  }

  @Test
  def setIdentityShouldSucceedWhenValidEmailProperty(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl]).addUserAliasMapping(bobAliasLocalPart, DOMAIN.asString, bobUsername.asString)

    val request: String =
      s"""{
        |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
        |	"methodCalls": [
        |		[
        |			"Identity/set",
        |			{
        |				"accountId": "$bobAccountId",
        |				"create": {
        |					"4f28": {
        |						"name": "valid send from identity",
        |						"email": "${bobUsername.asString}"
        |					},
        |					"4f29": {
        |						"name": "valid send from identity",
        |						"email": "${bobAliasAddress}"
        |					}
        |				}
        |			},
        |			"c1"
        |		]
        |	]
        |}""".stripMargin

    val response: String = `given`
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
           |	"accountId": "$bobAccountId",
           |	"newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"created": {
           |		"4f28": {
           |			"id": "$${json-unit.ignore}",
           |			"textSignature": "",
           |			"htmlSignature": "",
           |			"mayDelete": true
           |		},
           |		"4f29": {
           |			"id": "$${json-unit.ignore}",
           |			"textSignature": "",
           |			"htmlSignature": "",
           |			"mayDelete": true
           |		}
           |	}
           |}""".stripMargin)
  }

  @Test
  def setIdentityShouldFailWhenEmailPropertyIsMissing(): Unit = {
    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		[
         |			"Identity/set",
         |			{
         |				"accountId": "$bobAccountId",
         |				"create": {
         |					"4f29": {
         |						"name": "Bob",
         |						"replyTo": [{
         |							"name": "Alice",
         |							"email": "alice@domain.tld"
         |						}],
         |						"bcc": [{
         |							"name": "David",
         |							"email": "david@domain.tld"
         |						}],
         |						"textSignature": "Some text signature",
         |						"htmlSignature": "<p>Some html signature</p>"
         |					}
         |				}
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin

    val response =  `given`
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
           |	"accountId": "$bobAccountId",
           |	"newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"notCreated": {
           |		"4f29": {
           |			"type": "invalidArguments",
           |			"description": "Missing '/email' property"
           |		}
           |	}
           |}""".stripMargin)
  }

  @Test
  def setIdentityShouldFailWhenEmailPropertyIsNull(): Unit = {
    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		[
         |			"Identity/set",
         |			{
         |				"accountId": "$bobAccountId",
         |				"create": {
         |					"4f29": {
         |						"name": "Bob",
         |						"email": null,
         |						"replyTo": [{
         |							"name": "Alice",
         |							"email": "alice@domain.tld"
         |						}],
         |						"bcc": [{
         |							"name": "David",
         |							"email": "david@domain.tld"
         |						}],
         |						"textSignature": "Some text signature",
         |						"htmlSignature": "<p>Some html signature</p>"
         |					}
         |				}
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin

    val response =  `given`
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
           |	"accountId": "$bobAccountId",
           |	"newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"notCreated": {
           |		"4f29": {
           |			"type": "invalidArguments",
           |			"description": "'/email' property is not valid: mail address needs to be represented with a JsString"
           |		}
           |	}
           |}""".stripMargin)
  }

  @Test
  def setIdentityShouldNotCreatedWhenCreationRequestHasServerSetProperty(): Unit = {
    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		[
         |			"Identity/set",
         |			{
         |				"accountId": "$bobAccountId",
         |				"create": {
         |					"4f29": {
         |						"id": "someId",
         |            "mayDelete": false,
         |						"email": "${bobUsername.asString}"
         |					}
         |				}
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin

    val response =  `given`
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
           |	"accountId": "$bobAccountId",
           |	"newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"notCreated": {
           |		"4f29": {
           |			"type": "invalidArguments",
           |			"description": "Some server-set properties were specified",
           |			"properties": ["id", "mayDelete"]
           |		}
           |	}
           |}""".stripMargin)
  }

  @Test
  def setIdentityShouldFailWhenWrongAccountId(): Unit = {
    val request =
      s"""
         |{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		[
         |			"Identity/set",
         |			{
         |				"accountId": "unknownAccountId",
         |				"create": {}
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin

    val response =  `given`
      .body(request)
    .when
      .post
    .`then`
      .log().ifValidationFails()
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [
         |    ["error", {
         |      "type": "accountNotFound"
         |    }, "c1"]
         |  ]
         |}""".stripMargin)
  }

  @Test
  def setIdentityShouldFailWhenMissingCapability(): Unit = {
    val request: String =
      s"""{
        |	"using": [],
        |	"methodCalls": [
        |		[
        |			"Identity/set",
        |			{
        |				"accountId": "$bobAccountId",
        |				"create": {
        |					"4f29": {
        |						"email": "${bobUsername.asString}"
        |					}
        |				}
        |			},
        |			"c1"
        |		]
        |	]
        |}""".stripMargin

    val response: String = `given`
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
           |                "type": "unknownMethod",
           |                "description": "Missing capability(ies): urn:ietf:params:jmap:core, urn:ietf:params:jmap:submission"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def updateShouldSucceed(): Unit = {
    val identityId: String = createNewIdentity()
    val response: String = `given`
      .body(
        s"""{
           |    "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission" ],
           |    "methodCalls": [
           |        [
           |            "Identity/set",
           |            {
           |                "accountId": "$bobAccountId",
           |                "update": {
           |                    "$identityId": {
           |                        "name": "NewName1"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
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
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "Identity/set",
           |            {
           |                "accountId": "$bobAccountId",
           |                "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |                "updated": {
           |                    "$identityId": {}
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}
           |""".stripMargin)
  }

  @Test
  def updateShouldModifyIdentityEntry(): Unit = {
    val identityId: String = createNewIdentity()
    val response: String = `given`
      .body(
        s"""{
           |    "using": [
           |        "urn:ietf:params:jmap:core",
           |        "urn:ietf:params:jmap:submission"
           |    ],
           |    "methodCalls": [
           |        [
           |            "Identity/set",
           |            {
           |                "accountId": "$bobAccountId",
           |                "update": {
           |                    "$identityId": {
           |                        "name": "NewName1",
           |                        "replyTo": [
           |                            {
           |                                "name": "Difference Alice",
           |                                "email": "alice2@domain.tld"
           |                            }
           |                        ],
           |                        "bcc": [
           |                            {
           |                                "name": "Difference David",
           |                                "email": "david2@domain.tld"
           |                            }
           |                        ],
           |                        "textSignature": "Difference text signature",
           |                        "htmlSignature": "<p>Difference html signature</p>"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ],
           |        [
           |            "Identity/get",
           |            {
           |                "accountId": "$bobAccountId",
           |                "ids": [
           |                    "$identityId"
           |                ]
           |            },
           |            "c2"
           |        ]
           |    ]
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
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "Identity/set",
           |            {
           |                "accountId": "$bobAccountId",
           |                "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |                "updated": {
           |                    "$identityId": { }
           |                }
           |            },
           |            "c1"
           |        ],
           |        [
           |            "Identity/get",
           |            {
           |                "accountId": "$bobAccountId",
           |                "state": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |                "list": [
           |                    {
           |                        "id": "$identityId",
           |                        "name": "NewName1",
           |                        "email": "${bobUsername.asString}",
           |                        "replyTo": [
           |                            {
           |                                "name": "Difference Alice",
           |                                "email": "alice2@domain.tld"
           |                            }
           |                        ],
           |                        "bcc": [
           |                            {
           |                                "name": "Difference David",
           |                                "email": "david2@domain.tld"
           |                            }
           |                        ],
           |                        "textSignature": "Difference text signature",
           |                        "htmlSignature": "<p>Difference html signature</p>",
           |                        "mayDelete": true
           |                    }
           |                ]
           |            },
           |            "c2"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def updateShouldModifyIdentityEntryWhenSortOrder(): Unit = {
    val identityId: String = createNewIdentity()
    val response: String = `given`
      .body(
        s"""{
           |    "using": [
           |        "urn:ietf:params:jmap:core",
           |        "urn:ietf:params:jmap:submission",
           |        "urn:apache:james:params:jmap:mail:identity:sortorder"
           |    ],
           |    "methodCalls": [
           |        [
           |            "Identity/set",
           |            {
           |                "accountId": "$bobAccountId",
           |                "update": {
           |                    "$identityId": {
           |                        "name": "NewName1",
           |                        "replyTo": [
           |                            {
           |                                "name": "Difference Alice",
           |                                "email": "alice2@domain.tld"
           |                            }
           |                        ],
           |                        "bcc": [
           |                            {
           |                                "name": "Difference David",
           |                                "email": "david2@domain.tld"
           |                            }
           |                        ],
           |                        "textSignature": "Difference text signature",
           |                        "htmlSignature": "<p>Difference html signature</p>",
           |                        "sortOrder": 125
           |                    }
           |                }
           |            },
           |            "c1"
           |        ],
           |        [
           |            "Identity/get",
           |            {
           |                "accountId": "$bobAccountId",
           |                "ids": ["$identityId"]
           |            },
           |            "c2"
           |        ]
           |    ]
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
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "Identity/set",
           |            {
           |                "accountId": "$bobAccountId",
           |                "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |                "updated": {
           |                    "$identityId": { }
           |                }
           |            },
           |            "c1"
           |        ],
           |        [
           |            "Identity/get",
           |            {
           |                "accountId": "$bobAccountId",
           |                "state": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |                "list": [
           |                    {
           |                        "id": "$identityId",
           |                        "name": "NewName1",
           |                        "email": "${bobUsername.asString}",
           |                        "replyTo": [
           |                            {
           |                                "name": "Difference Alice",
           |                                "email": "alice2@domain.tld"
           |                            }
           |                        ],
           |                        "bcc": [
           |                            {
           |                                "name": "Difference David",
           |                                "email": "david2@domain.tld"
           |                            }
           |                        ],
           |                        "textSignature": "Difference text signature",
           |                        "htmlSignature": "<p>Difference html signature</p>",
           |                        "mayDelete": true,
           |                        "sortOrder": 125
           |                    }
           |                ]
           |            },
           |            "c2"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def updateShouldModifyIdentityEntryWhenBccNameIsNull(): Unit = {
    val identityId: String = createNewIdentity()
    val response: String = `given`
      .body(
        s"""{
           |    "using": [
           |        "urn:ietf:params:jmap:core",
           |        "urn:ietf:params:jmap:submission"
           |    ],
           |    "methodCalls": [
           |        [
           |            "Identity/set",
           |            {
           |                "accountId": "$bobAccountId",
           |                "update": {
           |                    "$identityId": {
           |                        "bcc": [
           |                            {
           |                                "name": null,
           |                                "email": "david2@domain.tld"
           |                            }
           |                        ]
           |                    }
           |                }
           |            },
           |            "c1"
           |        ],
           |        [
           |            "Identity/get",
           |            {
           |                "accountId": "$bobAccountId",
           |                "ids": [
           |                    "$identityId"
           |                ]
           |            },
           |            "c2"
           |        ]
           |    ]
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
      .isEqualTo(s"""{
                    |    "sessionState": "${SESSION_STATE.value}",
                    |    "methodResponses": [
                    |        [
                    |            "Identity/set",
                    |            {
                    |                "accountId": "$bobAccountId",
                    |                "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
                    |                "updated": {
                    |                    "$identityId": { }
                    |                }
                    |            },
                    |            "c1"
                    |        ],
                    |        [
                    |            "Identity/get",
                    |            {
                    |                "accountId": "$bobAccountId",
                    |                "state": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
                    |                "list": [
                    |                    {
                    |                        "name": "Bob",
                    |                        "htmlSignature": "<p>Some html signature</p>",
                    |                        "id": "$identityId",
                    |                        "bcc": [
                    |                            {
                    |                                "email": "david2@domain.tld"
                    |                            }
                    |                        ],
                    |                        "textSignature": "Some text signature",
                    |                        "mayDelete": true,
                    |                        "email": "${bobUsername.asString}",
                    |                        "replyTo": [
                    |                            {
                    |                                "name": "Alice",
                    |                                "email": "alice@domain.tld"
                    |                            }
                    |                        ]
                    |                    }
                    |                ]
                    |            },
                    |            "c2"
                    |        ]
                    |    ]
                    |}""".stripMargin)
  }

  @Test
  def updateShouldNotUpdatedWhenIdNotfound(): Unit = {
    val notfoundIdentityId: String = UUID.randomUUID().toString
    val response: String = `given`
      .body(
        s"""{
           |    "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission" ],
           |    "methodCalls": [
           |        [
           |            "Identity/set",
           |            {
           |            		"accountId": "$bobAccountId",
           |                "update": {
           |                    "$notfoundIdentityId": {
           |                        "name": "NewName1"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
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
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "Identity/set",
           |            {
           |                "accountId": "$bobAccountId",
           |                "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |                "notUpdated": {
           |                    "$notfoundIdentityId": {
           |                        "type": "notFound",
           |                        "description": "IdentityId($notfoundIdentityId) could not be found"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def updateShouldNotUpdatedWhenIdNotParsed(): Unit = {
    val notParsedId: String = "k123"
    val response: String = `given`
      .body(
        s"""{
           |    "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission" ],
           |    "methodCalls": [
           |        [
           |            "Identity/set",
           |            {
           |            		"accountId": "$bobAccountId",
           |                "update": {
           |                    "$notParsedId": {
           |                        "name": "NewName1"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
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
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "Identity/set",
           |            {
           |                "accountId": "$bobAccountId",
           |                "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |                "notUpdated": {
           |                    "$notParsedId": {
           |                        "type": "invalidArguments",
           |                        "description": "Invalid UUID string: $notParsedId"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def updateShouldNotUpdatedWhenAssignServerSetProperty(): Unit = {
    val identityId: String = createNewIdentity()
    val response: String = `given`
      .body(
        s"""{
           |    "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission" ],
           |    "methodCalls": [
           |        [
           |            "Identity/set",
           |            {
           |            		"accountId": "$bobAccountId",
           |                "update": {
           |                    "$identityId": {
           |                        "email": "bob2@domain.tld"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
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
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "Identity/set",
           |            {
           |                "accountId": "$bobAccountId",
           |                "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |                "notUpdated": {
           |                    "$identityId": {
           |                        "type": "invalidArguments",
           |                        "description": "Some server-set properties were specified",
           |                        "properties": [
           |                            "email"
           |                        ]
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def updateShouldSuccessWhenMixed(): Unit = {
    val updateIdentityId1: String = createNewIdentity()
    val updateIdentityId2: String = createNewIdentity()
    val notUpdateIdentityId1: String = UUID.randomUUID().toString
    val notUpdateIdentityId2: String = "notParsedId"

    val response: String = `given`
      .body(
        s"""{
           |    "using": [ "urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission" ],
           |    "methodCalls": [
           |        [
           |            "Identity/set",
           |            {
           |                "accountId": "$bobAccountId",
           |                "update": {
           |                    "$updateIdentityId1": { "name": "new Name 1" },
           |                    "$updateIdentityId2": { "name": "new Name 2" },
           |                    "$notUpdateIdentityId1": { "name": "new Name 3" },
           |                    "$notUpdateIdentityId2": { "name": "new Name 4" }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
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
      .withOptions(IGNORING_ARRAY_ORDER)
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "Identity/set",
           |            {
           |                "accountId": "$bobAccountId",
           |                "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |                "updated": {
           |                    "$updateIdentityId1": {},
           |                    "$updateIdentityId2": {}
           |                },
           |                "notUpdated": {
           |                    "$notUpdateIdentityId1": {
           |                        "type": "notFound",
           |                        "description": "IdentityId($notUpdateIdentityId1) could not be found"
           |                    },
           |                    "$notUpdateIdentityId2": {
           |                        "type": "invalidArguments",
           |                        "description": "Invalid UUID string: $notUpdateIdentityId2"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  private def createNewIdentity(): String = createNewIdentity(UUID.randomUUID().toString)

  private def createNewIdentity(clientId: String, email: String = bobUsername.asString): String =
    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
           |	"methodCalls": [
           |		[
           |			"Identity/set",
           |			{
           |				"accountId": "$bobAccountId",
           |				"create": {
           |					"$clientId": {
           |						"name": "Bob",
           |						"email": "$email",
           |						"replyTo": [{
           |							"name": "Alice",
           |							"email": "alice@domain.tld"
           |						}],
           |						"bcc": [{
           |							"name": "David",
           |							"email": "david@domain.tld"
           |						}],
           |						"textSignature": "Some text signature",
           |						"htmlSignature": "<p>Some html signature</p>"
           |					}
           |				}
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .extract()
      .jsonPath()
      .get(s"methodResponses[0][1].created.$clientId.id")

  @Test
  def destroyShouldSucceedWhenDeleteCustomIdentity(server: GuiceJamesServer): Unit = {
    val id = SMono(server.getProbe(classOf[IdentityProbe])
      .save(bobUsername, identityCreationRequest))
      .block()
      .id.id.toString

    val request: String =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		[
         |			"Identity/set",
         |			{
         |				"accountId": "$bobAccountId",
         |				"destroy": ["$id"]
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin

    val response: String = `given`
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
           |			"Identity/set",
           |			{
           |				"accountId": "$bobAccountId",
           |				"newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |				"destroyed": ["$id"]
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
  }

  @Test
  def destroyShouldFailWhenDeleteServerSetIdentities(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl]).addUserAliasMapping(bobAliasLocalPart, DOMAIN.asString, bobUsername.asString)
    val defaultServerSetIdentity = UUID.nameUUIDFromBytes(bobUsername.asString.getBytes(StandardCharsets.UTF_8))
    val serverIdentitiesId1 = UUID.nameUUIDFromBytes(bobAliasAddress.getBytes(StandardCharsets.UTF_8))

    val request: String =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		[
         |			"Identity/set",
         |			{
         |				"accountId": "$bobAccountId",
         |				"destroy": ["$serverIdentitiesId1", "$defaultServerSetIdentity"]
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin

    val response: String = `given`
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
           |			"Identity/set",
           |			{
           |				"accountId": "$bobAccountId",
           |				"newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |				"notDestroyed": {
           |					"$serverIdentitiesId1": {
           |						"type": "forbidden",
           |						"description": "User do not have permission to delete IdentityId($serverIdentitiesId1)"
           |					},
           |					"$defaultServerSetIdentity": {
           |						"type": "forbidden",
           |						"description": "User do not have permission to delete IdentityId($defaultServerSetIdentity)"
           |					}
           |				}
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
  }

  @Test
  def destroyShouldFailWhenInvalidId(): Unit = {
    val request: String =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		[
         |			"Identity/set",
         |			{
         |				"accountId": "$bobAccountId",
         |				"destroy": ["invalid"]
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin

    val response: String = `given`
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
           |			"Identity/set",
           |			{
           |				"accountId": "$bobAccountId",
           |				"newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |				"notDestroyed": {
           |					"invalid": {
           |						"type": "invalidArguments",
           |						"description": "invalid is not a IdentityId: Invalid UUID string: invalid"
           |					}
           |				}
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
  }

  @Test
  def destroyShouldNotFailWhenUnknownId(): Unit = {
    val id = UUID.randomUUID().toString

    val request: String =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		[
         |			"Identity/set",
         |			{
         |				"accountId": "$bobAccountId",
         |				"destroy": ["$id"]
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin

    val response: String = `given`
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
           |			"Identity/set",
           |			{
           |				"accountId": "$bobAccountId",
           |				"newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |				"destroyed": ["$id"]
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
  }

  @Test
  def destroyShouldHandleMixedCases(server: GuiceJamesServer): Unit = {
    val customId1 = SMono(server.getProbe(classOf[IdentityProbe])
      .save(bobUsername, identityCreationRequest))
      .block()
      .id.id.toString
    val customId2 = SMono(server.getProbe(classOf[IdentityProbe])
      .save(bobUsername, identityCreationRequest))
      .block()
      .id.id.toString
    val defaultServerSetIdentity = UUID.nameUUIDFromBytes(bobUsername.asString.getBytes(StandardCharsets.UTF_8))

    val request: String =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		[
         |			"Identity/set",
         |			{
         |				"accountId": "$bobAccountId",
         |				"destroy": ["$customId1", "$customId2", "$defaultServerSetIdentity"]
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin

    val response: String = `given`
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
      .when(net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER)
      .isEqualTo(
        s"""{
           |	"sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"methodResponses": [
           |		[
           |			"Identity/set",
           |			{
           |				"accountId": "$bobAccountId",
           |				"newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |				"destroyed": ["$customId1", "$customId2"],
           |				"notDestroyed": {
           |					"$defaultServerSetIdentity": {
           |						"type": "forbidden",
           |						"description": "User do not have permission to delete IdentityId($defaultServerSetIdentity)"
           |					}
           |				}
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
  }

  @Test
  def deletedIdentityShouldNotBeFetchedAnyMore(server: GuiceJamesServer): Unit = {
    val id = SMono(server.getProbe(classOf[IdentityProbe])
      .save(bobUsername, identityCreationRequest))
      .block()
      .id.id.toString

    val request1: String =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		[
         |			"Identity/set",
         |			{
         |				"accountId": "$bobAccountId",
         |				"destroy": ["$id"]
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin

    `given`
      .body(request1)
    .when
      .post
    .`then`
      .statusCode(SC_OK)

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
           |	"accountId": "$bobAccountId",
           |	"notFound": [
           |		"$id"
           |	],
           |	"state": "${INSTANCE.value}",
           |	"list": []
           |}""".stripMargin)
  }

  @Test
  def updateShouldAcceptServerSetId(): Unit = {
    val identityId: String = `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
           |	"methodCalls": [
           |		["Identity/get",
           |			{
           |				"accountId": "$bobAccountId",
           |				"ids": null
           |			}, "c2"
           |		]
           |
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .extract()
      .jsonPath()
      .get("methodResponses[0][1].list[0].id")

    val response: String = `given`
      .body(
        s"""{
           |    "using": [
           |        "urn:ietf:params:jmap:core",
           |        "urn:ietf:params:jmap:submission"
           |    ],
           |    "methodCalls": [
           |        [
           |            "Identity/set",
           |            {
           |                "accountId": "$bobAccountId",
           |                "update": {
           |                    "$identityId": {
           |                        "name": "NewName1",
           |                        "replyTo": [
           |                            {
           |                                "name": "Difference Alice",
           |                                "email": "alice2@domain.tld"
           |                            }
           |                        ],
           |                        "bcc": [
           |                            {
           |                                "name": "Difference David",
           |                                "email": "david2@domain.tld"
           |                            }
           |                        ],
           |                        "textSignature": "Difference text signature",
           |                        "htmlSignature": "<p>Difference html signature</p>"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ],
           |        [
           |            "Identity/get",
           |            {
           |                "accountId": "$bobAccountId",
           |                "ids": [
           |                    "$identityId"
           |                ]
           |            },
           |            "c2"
           |        ]
           |    ]
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
      .isEqualTo(
        s"""{
           |    "sessionState": "${SESSION_STATE.value}",
           |    "methodResponses": [
           |        [
           |            "Identity/set",
           |            {
           |                "accountId": "$bobAccountId",
           |                "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |                "updated": {
           |                    "$identityId": { }
           |                }
           |            },
           |            "c1"
           |        ],
           |        [
           |            "Identity/get",
           |            {
           |                "accountId": "$bobAccountId",
           |                "state": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |                "list": [
           |                    {
           |                        "id": "$identityId",
           |                        "name": "NewName1",
           |                        "email": "${bobUsername.asString}",
           |                        "replyTo": [
           |                            {
           |                                "name": "Difference Alice",
           |                                "email": "alice2@domain.tld"
           |                            }
           |                        ],
           |                        "bcc": [
           |                            {
           |                                "name": "Difference David",
           |                                "email": "david2@domain.tld"
           |                            }
           |                        ],
           |                        "textSignature": "Difference text signature",
           |                        "htmlSignature": "<p>Difference html signature</p>",
           |                        "mayDelete": false
           |                    }
           |                ]
           |            },
           |            "c2"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def givenUpdatedServerSetIdentityWhenAdminRemoveThatIdentityThenDestroyThatIdentityShouldSucceed(server: GuiceJamesServer): Unit = {
    // server create a alias for Bob
    server.getProbe(classOf[DataProbeImpl]).addUserAliasMapping(bobAliasLocalPart, DOMAIN.asString, bobUsername.asString)
    val serverIdentityId = UUID.nameUUIDFromBytes(bobAliasAddress.getBytes(StandardCharsets.UTF_8))

    // Bob update serverIdentity
    `given`
      .body(
        s"""{
           |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
           |	"methodCalls": [
           |		[
           |			"Identity/set",
           |			{
           |				"accountId": "$bobAccountId",
           |				"update": {
           |					"$serverIdentityId": {
           |						"name": "NewName1",
           |						"replyTo": [{
           |							"name": "Difference Alice",
           |							"email": "alice2@domain.tld"
           |						}],
           |						"bcc": [{
           |							"name": "Difference David",
           |							"email": "david2@domain.tld"
           |						}],
           |						"textSignature": "Difference text signature",
           |						"htmlSignature": "<p>Difference html signature</p>"
           |					}
           |				}
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)

    // server delete provided alias
    server.getProbe(classOf[DataProbeImpl]).removeUserAliasMapping(bobAliasLocalPart, DOMAIN.asString, bobUsername.asString)

    // Bob delete that serverIdentity
    val request: String =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		[
         |			"Identity/set",
         |			{
         |				"accountId": "$bobAccountId",
         |				"destroy": ["$serverIdentityId"]
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin

    val response: String = `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    // serverIdentity should be destroyed
    assertThatJson(response)
      .inPath("methodResponses[0][1]")
      .isEqualTo(
        s"""{
           |	"accountId": "$bobAccountId",
           |	"newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"destroyed": ["$serverIdentityId"]
           |}""".stripMargin)
  }

  @Test
  def givenServerSetAliasAndCreateACustomIdentityWithItWhenAdminRemoveThatAliasThenFetchThatIdentityShouldNoLongerReturn(server: GuiceJamesServer): Unit = {
    // GIVEN bob-alias@domain.tld
    server.getProbe(classOf[DataProbeImpl]).addUserAliasMapping(bobAliasLocalPart, DOMAIN.asString, bobUsername.asString)

    // Bob create a new custom identity with bob-alias@domain.tld
    val customIdentityId = createNewIdentity(UUID.randomUUID().toString, bobAliasAddress)

    // WHEN an admin delete bob-alias@domain.tld
    server.getProbe(classOf[DataProbeImpl]).removeUserAliasMapping(bobAliasLocalPart, DOMAIN.asString, bobUsername.asString)

    // THEN Identity/get no longer returns the identity
    val request: String =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		[
         |			"Identity/get",
         |			{
         |				"accountId": "$bobAccountId",
         |				"ids": ["$customIdentityId"]
         |			}, "c1"
         |		]
         |
         |	]
         |}""".stripMargin

    val response: String = `given`
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
           |	"accountId": "$bobAccountId",
           |	"notFound": [
           |		"$customIdentityId"
           |	],
           |	"state": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"list": []
           |}""".stripMargin)
  }

  @Test
  def givenServerSetAliasAndCreateACustomIdentityWhenAdminRemoveThatAliasThenUpdateThatIdentityShouldFail(server: GuiceJamesServer): Unit = {
    // GIVEN bob-alias@domain.tld
    server.getProbe(classOf[DataProbeImpl]).addUserAliasMapping(bobAliasLocalPart, DOMAIN.asString, bobUsername.asString)

    // Bob create a new custom identity with bob-alias@domain.tld
    val customIdentityId = createNewIdentity(UUID.randomUUID().toString, bobAliasAddress)

    // WHEN an admin delete bob-alias@domain.tld
    server.getProbe(classOf[DataProbeImpl]).removeUserAliasMapping(bobAliasLocalPart, DOMAIN.asString, bobUsername.asString)

    // THEN Identity/set update of that identity fails
    val request: String =
      s"""{
         |	"using": [
         |		"urn:ietf:params:jmap:core",
         |		"urn:ietf:params:jmap:submission"
         |	],
         |	"methodCalls": [
         |		[
         |			"Identity/set",
         |			{
         |				"accountId": "$bobAccountId",
         |				"update": {
         |					"$customIdentityId": {
         |						"name": "NewName1",
         |						"replyTo": [{
         |							"name": "Difference Alice",
         |							"email": "alice2@domain.tld"
         |						}],
         |						"bcc": [{
         |							"name": "Difference David",
         |							"email": "david2@domain.tld"
         |						}],
         |						"textSignature": "Difference text signature",
         |						"htmlSignature": "<p>Difference html signature</p>"
         |					}
         |				}
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin

    val response: String = `given`
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
           |	"accountId": "$bobAccountId",
           |	"newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"notUpdated": {
           |		"$customIdentityId": {
           |			"type": "notFound",
           |			"description": "IdentityId($customIdentityId) could not be found"
           |		}
           |	}
           |}""".stripMargin)
  }

  @Test
  def givenServerSetAliasAndUserUpdateItWhenAdminRemoveThatAliasThenFetchThatIdentityShouldNoLongerReturn(server: GuiceJamesServer): Unit = {
    // GIVEN bob-alias@domain.tld
    server.getProbe(classOf[DataProbeImpl]).addUserAliasMapping(bobAliasLocalPart, DOMAIN.asString, bobUsername.asString)
    val serverIdentityId = UUID.nameUUIDFromBytes(bobAliasAddress.getBytes(StandardCharsets.UTF_8))

    // Bob update the default identity of bob-alias@domain.tld
    `given`
      .body(
        s"""{
           |	"using": [
           |		"urn:ietf:params:jmap:core",
           |		"urn:ietf:params:jmap:submission"
           |	],
           |	"methodCalls": [
           |		[
           |			"Identity/set",
           |			{
           |				"accountId": "$bobAccountId",
           |				"update": {
           |					"$serverIdentityId": {
           |						"name": "NewName1",
           |						"replyTo": [{
           |							"name": "Difference Alice",
           |							"email": "alice2@domain.tld"
           |						}],
           |						"bcc": [{
           |							"name": "Difference David",
           |							"email": "david2@domain.tld"
           |						}],
           |						"textSignature": "Difference text signature",
           |						"htmlSignature": "<p>Difference html signature</p>"
           |					}
           |				}
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)

    // WHEN an admin delete bob-alias@domain.tld
    server.getProbe(classOf[DataProbeImpl]).removeUserAliasMapping(bobAliasLocalPart, DOMAIN.asString, bobUsername.asString)

    // THEN Identity/get that identity should no longer return
    val request: String =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		[
         |			"Identity/get",
         |			{
         |				"accountId": "$bobAccountId",
         |				"ids": ["$serverIdentityId"]
         |			}, "c1"
         |		]
         |	]
         |}""".stripMargin

    val response: String = `given`
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
           |	"accountId": "$bobAccountId",
           |	"notFound": [
           |		"$serverIdentityId"
           |	],
           |	"state": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"list": []
           |}""".stripMargin)
  }

  @Test
  def givenServerSetAliasAndUserUpdateItWhenAdminRemoveThatAliasThenUpdateThatIdentityShouldFail(server: GuiceJamesServer): Unit = {
    // GIVEN bob-alias@domain.tld
    server.getProbe(classOf[DataProbeImpl]).addUserAliasMapping(bobAliasLocalPart, DOMAIN.asString, bobUsername.asString)
    val serverIdentityId = UUID.nameUUIDFromBytes(bobAliasAddress.getBytes(StandardCharsets.UTF_8))

    // Bob update the default identity of bob-alias@domain.tld
    `given`
      .body(
        s"""{
           |	"using": [
           |		"urn:ietf:params:jmap:core",
           |		"urn:ietf:params:jmap:submission"
           |	],
           |	"methodCalls": [
           |		[
           |			"Identity/set",
           |			{
           |				"accountId": "$bobAccountId",
           |				"update": {
           |					"$serverIdentityId": {
           |						"name": "NewName1",
           |						"replyTo": [{
           |							"name": "Difference Alice",
           |							"email": "alice2@domain.tld"
           |						}],
           |						"bcc": [{
           |							"name": "Difference David",
           |							"email": "david2@domain.tld"
           |						}],
           |						"textSignature": "Difference text signature",
           |						"htmlSignature": "<p>Difference html signature</p>"
           |					}
           |				}
           |			},
           |			"c1"
           |		]
           |	]
           |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)

    // WHEN an admin delete bob-alias@domain.tld
    server.getProbe(classOf[DataProbeImpl]).removeUserAliasMapping(bobAliasLocalPart, DOMAIN.asString, bobUsername.asString)

    // THEN Identity/set update of that identity fails
    val request: String =
      s"""{
         |	"using": [
         |		"urn:ietf:params:jmap:core",
         |		"urn:ietf:params:jmap:submission"
         |	],
         |	"methodCalls": [
         |		[
         |			"Identity/set",
         |			{
         |				"accountId": "$bobAccountId",
         |				"update": {
         |					"$serverIdentityId": {
         |						"name": "NewName1",
         |						"replyTo": [{
         |							"name": "Difference Alice",
         |							"email": "alice2@domain.tld"
         |						}],
         |						"bcc": [{
         |							"name": "Difference David",
         |							"email": "david2@domain.tld"
         |						}],
         |						"textSignature": "Difference text signature",
         |						"htmlSignature": "<p>Difference html signature</p>"
         |					}
         |				}
         |			},
         |			"c1"
         |		]
         |	]
         |}""".stripMargin

    val response: String = `given`
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
           |	"accountId": "$bobAccountId",
           |	"newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"notUpdated": {
           |		"$serverIdentityId": {
           |			"type": "notFound",
           |			"description": "IdentityId($serverIdentityId) could not be found"
           |		}
           |	}
           |}""".stripMargin)
  }

}
