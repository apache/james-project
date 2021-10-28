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
import org.apache.james.jmap.core.UTCDate
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.jmap.rfc8621.contract.PushSubscriptionSetMethodContract.TIME_FORMATTER
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Test}

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object PushSubscriptionSetMethodContract {
  val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")
}

trait PushSubscriptionSetMethodContract {

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .addHeader(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .build()
  }

  @Test
  def setMethodShouldNotRequireAccountId(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": ["Mailbox"]
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
  }

  @Test
  def setMethodShouldFailWhenMissingCapability(): Unit = {
    val request: String =
      """{
        |    "using": [],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": ["Mailbox"]
        |                }
        |              }
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
           |            "error",
           |            {
           |                "type": "unknownMethod",
           |                "description": "Missing capability(ies): urn:ietf:params:jmap:core"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldNotCreatedWhenMissingTypesPropertyInCreationRequest(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086"
        |                }
        |              }
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
           |            "PushSubscription/set",
           |            {
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "Missing '/types' property in PushSubscription object"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldNotCreatedWhenTypesPropertyIsEmpty(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": []
        |                }
        |              }
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
           |            "PushSubscription/set",
           |            {
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "types must not be empty"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldNotCreatedWhenInvalidURLProperty(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "invalid",
        |                  "types": ["Mailbox"]
        |                }
        |              }
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
           |            "PushSubscription/set",
           |            {
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "'/url' property in PushSubscription object is not valid"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }


  @Test
  def setMethodShouldNotCreatedWhenCreationRequestHasVerificationCodeProperty(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": ["Mailbox"],
        |                  "verificationCode": "abc"
        |                }
        |              }
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
           |            "PushSubscription/set",
           |            {
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "Some server-set properties were specified",
           |                        "properties": [
           |                            "verificationCode"
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
  def setMethodShouldNotCreatedWhenCreationRequestHasIdProperty(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": ["Mailbox"],
        |                  "id": "abc"
        |                }
        |              }
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
           |            "PushSubscription/set",
           |            {
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "Some server-set properties were specified",
           |                        "properties": [
           |                            "id"
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
  def setMethodShouldNotCreatedWhenInValidExpiresProperty(): Unit = {
    val invalidExpire: String = UTCDate(ZonedDateTime.now().minusDays(1)).asUTC.format(TIME_FORMATTER)
    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [
         |      [
         |        "PushSubscription/set",
         |        {
         |            "create": {
         |                "4f29": {
         |                  "deviceClientId": "a889-ffea-910",
         |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
         |                  "expires": "$invalidExpire",
         |                  "types": ["Mailbox"]
         |                }
         |              }
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
           |            "PushSubscription/set",
           |            {
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "`$invalidExpire` expires must be greater than now"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldNotCreatedWhenInValidTypesProperty(): Unit = {
    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [
         |      [
         |        "PushSubscription/set",
         |        {
         |            "create": {
         |                "4f29": {
         |                  "deviceClientId": "a889-ffea-910",
         |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
         |                  "types": ["invalid"]
         |                }
         |              }
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
           |            "PushSubscription/set",
           |            {
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "'/types(0)' property in PushSubscription object is not valid"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldNotCreatedWhenDeviceClientIdExists(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": ["Mailbox"]
        |                }
        |              }
        |        },
        |        "c1"
        |      ]
        |    ]
        |  }""".stripMargin

    `given`
      .body(request)
    .when
      .post
    .`then`
      .statusCode(SC_OK)

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
           |            "PushSubscription/set",
           |            {
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "`a889-ffea-910` deviceClientId must be unique"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldAcceptValidExpiresProperty(): Unit = {
    val request: String =
      s"""{
         |    "using": ["urn:ietf:params:jmap:core"],
         |    "methodCalls": [
         |      [
         |        "PushSubscription/set",
         |        {
         |            "create": {
         |                "4f29": {
         |                  "deviceClientId": "a889-ffea-910",
         |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
         |                  "expires": "${UTCDate(ZonedDateTime.now().plusDays(1)).asUTC.format(TIME_FORMATTER)}",
         |                  "types": ["Mailbox"]
         |                }
         |              }
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
           |            "PushSubscription/set",
           |            {
           |                "created": {
           |                    "4f29": {
           |                        "id": "$${json-unit.ignore}",
           |                        "expires": "$${json-unit.ignore}"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldCreatedWhenValidRequest(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": ["Mailbox"]
        |                }
        |              }
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
           |            "PushSubscription/set",
           |            {
           |                "created": {
           |                    "4f29": {
           |                        "id": "$${json-unit.ignore}",
           |                        "expires": "$${json-unit.ignore}"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldCreatedSeveralValidCreationRequest(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f28": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": ["Mailbox"]
        |                },
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-912",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": ["Email"]
        |                }
        |              }
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
           |            "PushSubscription/set",
           |            {
           |                "created": {
           |                    "4f28": {
           |                        "id": "$${json-unit.ignore}",
           |                        "expires": "$${json-unit.ignore}"
           |                    },
           |                    "4f29": {
           |                        "id": "$${json-unit.ignore}",
           |                        "expires": "$${json-unit.ignore}"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

  @Test
  def setMethodShouldSuccessWhenMixCase(): Unit = {
    val request: String =
      """{
        |    "using": ["urn:ietf:params:jmap:core"],
        |    "methodCalls": [
        |      [
        |        "PushSubscription/set",
        |        {
        |            "create": {
        |                "4f28": {
        |                  "deviceClientId": "a889-ffea-910",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": ["Mailbox"]
        |                },
        |                "4f29": {
        |                  "deviceClientId": "a889-ffea-912",
        |                  "url": "https://example.com/push/?device=X8980fc&client=12c6d086",
        |                  "types": ["invalid"]
        |                }
        |              }
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
           |            "PushSubscription/set",
           |            {
           |                "created": {
           |                    "4f28": {
           |                        "id": "$${json-unit.ignore}",
           |                        "expires": "$${json-unit.ignore}"
           |                    }
           |                },
           |                "notCreated": {
           |                    "4f29": {
           |                        "type": "invalidArguments",
           |                        "description": "'/types(0)' property in PushSubscription object is not valid"
           |                    }
           |                }
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }
}
