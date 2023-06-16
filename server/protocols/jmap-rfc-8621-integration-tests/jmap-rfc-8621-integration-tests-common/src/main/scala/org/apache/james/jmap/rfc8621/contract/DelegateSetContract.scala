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

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured.{`given`, requestSpecification}
import io.restassured.http.ContentType.JSON
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.delegation.DelegationId
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.DelegateSetContract.BOB_ACCOUNT_ID
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, ANDRE, ANDRE_ACCOUNT_ID, ANDRE_PASSWORD, BOB, BOB_PASSWORD, CEDRIC, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.probe.DelegationProbe
import org.apache.james.utils.DataProbeImpl
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS
import org.junit.jupiter.api.{BeforeEach, Test}

import java.util.UUID
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._

object DelegateSetContract {
  val BOB_ACCOUNT_ID: String = Fixture.ACCOUNT_ID
}
trait DelegateSetContract {
  private lazy val slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS
  private lazy val calmlyAwait = Awaitility.`with`
    .pollInterval(slowPacedPollInterval)
    .and.`with`.pollDelay(slowPacedPollInterval)
    .await
  private lazy val awaitAtMostTenSeconds = calmlyAwait.atMost(10, TimeUnit.SECONDS)

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent
      .addDomain(DOMAIN.asString)
      .addUser(BOB.asString, BOB_PASSWORD)
      .addUser(ANDRE.asString(), ANDRE_PASSWORD)
      .addUser(CEDRIC.asString(), "secret")

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build
  }

  @Test
  def delegateSetShouldSucceed(server: GuiceJamesServer): Unit = {
    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:apache:james:params:jmap:delegation"],
         |	"methodCalls": [
         |		[
         |			"Delegate/set", {
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"create": {
         |					"4f29": {
         |						"username": "andre@domain.tld"
         |					}
         |				}
         |			}, "0"
         |		]
         |	]
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
    val delegationId = DelegationId.from(BOB, ANDRE).serialize

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |	"sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"methodResponses": [
           |		["Delegate/set", {
           |			"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |			"newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |			"created": {
           |				"4f29": {
           |					"id": "$delegationId"
           |				}
           |			}
           |		}, "0"]
           |	]
           |}""".stripMargin)

    awaitAtMostTenSeconds.untilAsserted(() =>
      assertThat(server.getProbe(classOf[DelegationProbe]).getAuthorizedUsers(BOB).asJavaCollection)
        .containsExactly(ANDRE))
  }

  @Test
  def delegateSetWithSeveralCreationRequestsShouldSucceed(server: GuiceJamesServer): Unit = {
    val delegationProbe = server.getProbe(classOf[DelegationProbe])
    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:apache:james:params:jmap:delegation"],
         |	"methodCalls": [
         |		[
         |			"Delegate/set", {
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"create": {
         |					"4f29": {
         |						"username": "andre@domain.tld"
         |					},
         |					"4f30": {
         |						"username": "cedric@domain.tld"
         |					}
         |				}
         |			}, "0"
         |		]
         |	]
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
    val andreDelegationId = DelegationId.from(BOB, ANDRE).serialize
    val cedricDelegationId = DelegationId.from(BOB, CEDRIC).serialize

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |	"sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"methodResponses": [
           |		["Delegate/set", {
           |			"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |			"newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |			"created": {
           |				"4f29": {
           |					"id": "$andreDelegationId"
           |				},
           |				"4f30": {
           |					"id": "$cedricDelegationId"
           |				}
           |			}
           |		}, "0"]
           |	]
           |}""".stripMargin)

    awaitAtMostTenSeconds.untilAsserted(() =>
      assertThat(delegationProbe.getAuthorizedUsers(BOB).asJavaCollection)
        .containsExactlyInAnyOrder(ANDRE, CEDRIC))
  }

  @Test
  def delegateSetShouldFailWhenUsernamePropertyIsMissing(): Unit = {
    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:apache:james:params:jmap:delegation"],
         |	"methodCalls": [
         |		[
         |			"Delegate/set", {
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"create": {
         |					"4f29": {
         |					}
         |				}
         |			}, "0"
         |		]
         |	]
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
           |			"Delegate/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |				"notCreated": {
           |					"4f29": {
           |						"type": "invalidArguments",
           |						"description": "Missing '/username' property"
           |					}
           |				}
           |			},
           |			"0"
           |		]
           |	]
           |}""".stripMargin)
  }

  @Test
  def delegateSetShouldFailWhenUsernameIsNull(): Unit = {
    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:apache:james:params:jmap:delegation"],
         |	"methodCalls": [
         |		[
         |			"Delegate/set", {
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"create": {
         |					"4f29": {
         |						"username": null
         |					}
         |				}
         |			}, "0"
         |		]
         |	]
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
           |			"Delegate/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |				"notCreated": {
           |					"4f29": {
           |						"type": "invalidArguments",
           |						"description": "'/username' property is not valid: username needs to be represented by a JsString"
           |					}
           |				}
           |			},
           |			"0"
           |		]
           |	]
           |}""".stripMargin)
  }

  @Test
  def delegateSetShouldFailWhenWrongAccountId(): Unit = {
    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:apache:james:params:jmap:delegation"],
         |	"methodCalls": [
         |		[
         |			"Delegate/set", {
         |				"accountId": "unknownAccountId",
         |				"create": {
         |					"4f29": {
         |						"username": "andre@domain.tld"
         |					}
         |				}
         |			}, "0"
         |		]
         |	]
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
           |			"error",
           |			{
           |				"type": "accountNotFound"
           |			},
           |			"0"
           |		]
           |	]
           |}""".stripMargin)
  }

  @Test
  def delegateSetShouldFailWhenMissingDelegationCapability(): Unit = {
    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core"],
         |	"methodCalls": [
         |		[
         |			"Delegate/set", {
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"create": {
         |					"4f29": {
         |						"username": "andre@domain.tld"
         |					}
         |				}
         |			}, "0"
         |		]
         |	]
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
           |			"error",
           |			{
           |				"type": "unknownMethod",
           |				"description": "Missing capability(ies): urn:apache:james:params:jmap:delegation"
           |			},
           |			"0"
           |		]
           |	]
           |}""".stripMargin)
  }

  @Test
  def delegateSetShouldFailWhenUserDoesNotExist(): Unit = {
    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:apache:james:params:jmap:delegation"],
         |	"methodCalls": [
         |		[
         |			"Delegate/set", {
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"create": {
         |					"4f29": {
         |						"username": "nonexistuser@domain.tld"
         |					}
         |				}
         |			}, "0"
         |		]
         |	]
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
           |			"Delegate/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |				"notCreated": {
           |					"4f29": {
           |						"type": "invalidArguments",
           |						"description": "User nonexistuser@domain.tld does not exist"
           |					}
           |				}
           |			},
           |			"0"
           |		]
           |	]
           |}""".stripMargin)
  }

  @Test
  def delegateSetShouldBeIdempotent(server: GuiceJamesServer): Unit = {
    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:apache:james:params:jmap:delegation"],
         |	"methodCalls": [
         |		[
         |			"Delegate/set", {
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"create": {
         |					"4f29": {
         |						"username": "andre@domain.tld"
         |					},
         |					"4f30": {
         |						"username": "andre@domain.tld"
         |					}
         |				}
         |			}, "0"
         |		]
         |	]
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
    val delegationId = DelegationId.from(BOB, ANDRE).serialize

    assertThatJson(response)
      .isEqualTo(
        s"""{
           |	"sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"methodResponses": [
           |		["Delegate/set", {
           |			"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |			"newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |			"created": {
           |				"4f29": {
           |					"id": "$delegationId"
           |				},
           |				"4f30": {
           |					"id": "$delegationId"
           |				}
           |			}
           |		}, "0"]
           |	]
           |}""".stripMargin)

    awaitAtMostTenSeconds.untilAsserted(() =>
      assertThat(server.getProbe(classOf[DelegationProbe]).getAuthorizedUsers(BOB).asJavaCollection)
        .containsExactly(ANDRE))
  }

  @Test
  def shouldReturnNotFoundWhenNotDelegated(): Unit = {
    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:apache:james:params:jmap:delegation"],
         |	"methodCalls": [
         |		[
         |			"Delegate/set", {
         |				"accountId": "$ANDRE_ACCOUNT_ID",
         |				"create": {
         |					"4f29": {
         |						"username": "cedric@domain.tld"
         |					}
         |				}
         |			}, "0"
         |		]
         |	]
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
           |	"type": "accountNotFound"
           |}""".stripMargin)
  }

  @Test
  def bobCanOnlyManageHisPrimaryAccountSetting(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe]).addAuthorizedUser(ANDRE, BOB)
    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:apache:james:params:jmap:delegation"],
         |	"methodCalls": [
         |		[
         |			"Delegate/set", {
         |				"accountId": "$ANDRE_ACCOUNT_ID",
         |				"create": {
         |					"4f29": {
         |						"username": "cedric@domain.tld"
         |					}
         |				}
         |			}, "0"
         |		]
         |	]
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
      .inPath("methodResponses[0]")
      .isEqualTo(
        s"""[
           |	"error",
           |	{
           |		"type": "forbidden",
           |		"description": "Access to other accounts settings is forbidden"
           |	},
           |	"0"
           |]""".stripMargin)
  }

  @Test
  def destroyShouldSucceed(server: GuiceJamesServer) : Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(BOB, ANDRE)

    val delegationId = DelegationId.from(BOB, ANDRE).serialize

    val request: String =
      s"""{
         |	  "using": ["urn:ietf:params:jmap:core", "urn:apache:james:params:jmap:delegation"],
         |    "methodCalls": [
         |      [
         |        "Delegate/set",
         |        {
         |            "accountId": "$BOB_ACCOUNT_ID",
         |            "destroy": ["${delegationId}"]
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

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
           |            "Delegate/set",
           |            {
           |                "accountId": "$BOB_ACCOUNT_ID",
           |                "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |                "destroyed": ["${delegationId}"]
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)

    assertThat(server.getProbe(classOf[DelegationProbe]).getAuthorizedUsers(BOB)
      .asJava).isEmpty()
  }

  @Test
  def destroyShouldFailWhenInvalidId(): Unit = {
    val request: String =
      s"""{
         |	  "using": ["urn:ietf:params:jmap:core", "urn:apache:james:params:jmap:delegation"],
         |    "methodCalls": [
         |      [
         |        "Delegate/set",
         |        {
         |            "accountId": "$BOB_ACCOUNT_ID",
         |            "destroy": ["invalid"]
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

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
           |            "Delegate/set",
           |            {
           |                "accountId": "$BOB_ACCOUNT_ID",
           |                "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |                "notDestroyed": {
           |                    "invalid": {
           |                        "type": "invalidArguments",
           |                        "description": "invalid is not a DelegationId: Invalid UUID string: invalid"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def destroyShouldNotFailWhenUnknownId(): Unit = {
    val id = UUID.randomUUID().toString

    val request: String =
      s"""{
         |	  "using": ["urn:ietf:params:jmap:core", "urn:apache:james:params:jmap:delegation"],
         |    "methodCalls": [
         |      [
         |        "Delegate/set",
         |        {
         |            "accountId": "$BOB_ACCOUNT_ID",
         |            "destroy": ["$id"]
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

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
           |            "Delegate/set",
           |            {
           |                "accountId": "$BOB_ACCOUNT_ID",
           |                "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |                "destroyed": ["$id"]
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def destroyShouldHandleMixedCases(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(BOB, ANDRE)
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(BOB, CEDRIC)

    val delegationId = DelegationId.from(BOB, ANDRE).serialize

    val request: String =
      s"""{
         |	  "using": ["urn:ietf:params:jmap:core", "urn:apache:james:params:jmap:delegation"],
         |    "methodCalls": [
         |      [
         |        "Delegate/set",
         |        {
         |            "accountId": "$BOB_ACCOUNT_ID",
         |            "destroy": ["$delegationId"]
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

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
           |            "Delegate/set",
           |            {
           |                "accountId": "$BOB_ACCOUNT_ID",
           |                "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |                "destroyed": ["$delegationId"]
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def destroyShouldNotRemoveUnAssignId(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(BOB, ANDRE)
    server.getProbe(classOf[DelegationProbe])
      .addAuthorizedUser(BOB, CEDRIC)

    val delegationId = DelegationId.from(BOB, ANDRE).serialize

    val request: String =
      s"""{
         |	  "using": ["urn:ietf:params:jmap:core", "urn:apache:james:params:jmap:delegation"],
         |    "methodCalls": [
         |      [
         |        "Delegate/set",
         |        {
         |            "accountId": "$BOB_ACCOUNT_ID",
         |            "destroy": ["$delegationId"]
         |        },
         |        "c1"
         |      ]
         |    ]
         |  }""".stripMargin

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
           |            "Delegate/set",
           |            {
           |                "accountId": "$BOB_ACCOUNT_ID",
           |                "newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |                "destroyed": ["$delegationId"]
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)

    assertThat(server.getProbe(classOf[DelegationProbe]).getAuthorizedUsers(BOB)
      .asJava).containsExactlyInAnyOrder(CEDRIC)
  }
}
