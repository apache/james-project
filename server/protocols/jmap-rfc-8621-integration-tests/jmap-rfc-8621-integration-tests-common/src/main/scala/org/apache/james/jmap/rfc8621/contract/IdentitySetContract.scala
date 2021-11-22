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
import org.apache.james.jmap.core.UuidState.INSTANCE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.rfc8621.contract.Fixture.{ACCEPT_RFC8621_VERSION_HEADER, BOB, BOB_PASSWORD, DOMAIN, authScheme, baseRequestSpecBuilder}
import org.apache.james.utils.DataProbeImpl
import org.junit.jupiter.api.{BeforeEach, Test}

trait IdentitySetContract {
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
  def setIdentityShouldSucceed(): Unit = {
    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		[
         |			"Identity/set",
         |			{
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"create": {
         |					"4f29": {
         |						"name": "Bob",
         |						"email": "bob@domain.tld",
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
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"ids": null
         |			}, "c2"
         |		]
         |
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
      .when(net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER)
      .isEqualTo(
        s"""{
           |	"sessionState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"methodResponses": [
           |		[
           |			"Identity/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"state": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |				"list": [{
           |						"id": "$${json-unit.ignore}",
           |						"name": "Bob",
           |						"email": "bob@domain.tld",
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
           |						"id": "becaf930-ea9e-3ef4-81ea-206eecb04aa7",
           |						"name": "bob@domain.tld",
           |						"email": "bob@domain.tld",
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
  def setIdentityAndGetIdentityCombinedShouldSucceed(): Unit = {
    val request =
      s"""{
         |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
         |	"methodCalls": [
         |		[
         |			"Identity/set",
         |			{
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"create": {
         |					"4f29": {
         |						"name": "Bob",
         |						"email": "bob@domain.tld",
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
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"ids": ["#4f29"]
         |			}, "c2"
         |		]
         |
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
           |			"Identity/set",
           |			{
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
           |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |				"state": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |				"list": [{
           |					"id": "$${json-unit.ignore}",
           |					"name": "Bob",
           |					"email": "bob@domain.tld",
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
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"create": {
         |					"4f29": {
         |						"email": "bob@domain.tld"
         |					}
         |				}
         |			},
         |			"c1"
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
           |	"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
      """{
        |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
        |	"methodCalls": [
        |		[
        |			"Identity/set",
        |			{
        |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |				"create": {
        |					"4f28": {
        |						"name": "Identity1",
        |						"email": "bob@domain.tld",
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
        |						"email": "bob@domain.tld",
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
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
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
           |	"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
      """{
        |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
        |	"methodCalls": [
        |		[
        |			"Identity/set",
        |			{
        |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |				"create": {
        |					"4f28": {
        |						"name": "valid send from identity",
        |						"email": "bob@domain.tld"
        |					},
        |					"4f29": {
        |						"name": "forbidden send from identity",
        |						"email": "bob-alias@domain.tld"
        |					}
        |				}
        |			},
        |			"c1"
        |		]
        |	]
        |}""".stripMargin

    val response: String = `given`
      .body(request)
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
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
           |	"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
           |			"description": "Can not send from bob-alias@domain.tld"
           |		}
           |	}
           |}""".stripMargin)
  }

  @Test
  def setIdentityShouldSucceedWhenValidEmailProperty(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl]).addUserAliasMapping("bob-alias", "domain.tld", "bob@domain.tld")

    val request: String =
      """{
        |	"using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:submission"],
        |	"methodCalls": [
        |		[
        |			"Identity/set",
        |			{
        |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |				"create": {
        |					"4f28": {
        |						"name": "valid send from identity",
        |						"email": "bob@domain.tld"
        |					},
        |					"4f29": {
        |						"name": "valid send from identity",
        |						"email": "bob-alias@domain.tld"
        |					}
        |				}
        |			},
        |			"c1"
        |		]
        |	]
        |}""".stripMargin

    val response: String = `given`
      .body(request)
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
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
           |	"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
           |	"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |	"newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"notCreated": {
           |		"4f29": {
           |			"type": "invalidArguments",
           |			"description": "Missing '/email' property in Identity object",
           |			"properties": [
           |				"email"
           |			]
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
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
           |	"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
           |	"newState": "2c9f1b12-b35a-43e6-9af2-0106fb53a943",
           |	"notCreated": {
           |		"4f29": {
           |			"type": "invalidArguments",
           |			"description": "'/email' property in Identity object is not valid: mail address needs to be represented with a JsString"
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
         |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
         |				"create": {
         |					"4f29": {
         |						"id": "someId",
         |            "mayDelete": false,
         |						"email": "bob@domain.tld"
         |					}
         |				}
         |			},
         |			"c1"
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
           |	"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
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
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
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
      """{
        |	"using": [],
        |	"methodCalls": [
        |		[
        |			"Identity/set",
        |			{
        |				"accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
        |				"create": {
        |					"4f29": {
        |						"email": "bob@domain.tld"
        |					}
        |				}
        |			},
        |			"c1"
        |		]
        |	]
        |}""".stripMargin

    val response: String = `given`
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
           |                "type": "unknownMethod",
           |                "description": "Missing capability(ies): urn:ietf:params:jmap:core, urn:ietf:params:jmap:submission"
           |            },
           |            "c1"
           |        ]
           |    ]
           |}""".stripMargin)
  }

}
