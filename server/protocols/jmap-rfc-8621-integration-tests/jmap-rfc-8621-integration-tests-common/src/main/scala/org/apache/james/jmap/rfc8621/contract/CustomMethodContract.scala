/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 * http://www.apache.org/licenses/LICENSE-2.0                   *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.rfc8621.contract

import java.io.{ByteArrayInputStream, InputStream}
import java.net.URI
import java.nio.charset.StandardCharsets

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.NonNegative
import eu.timepit.refined.refineV
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured._
import io.restassured.http.ContentType.JSON
import javax.inject.{Inject, Named}
import net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson
import org.apache.http.HttpStatus.SC_OK
import org.apache.james.GuiceJamesServer
import org.apache.james.events.Event.EventId
import org.apache.james.events.EventBus
import org.apache.james.jmap.api.model.Size.Size
import org.apache.james.jmap.api.model.{AccountId, State, TypeName}
import org.apache.james.jmap.change.{AccountIdRegistrationKey, StateChangeEvent}
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.MethodName
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, UrlPrefixes}
import org.apache.james.jmap.draft.JmapGuiceProbe
import org.apache.james.jmap.http.UserCredential
import org.apache.james.jmap.mail
import org.apache.james.jmap.method.{InvocationWithContext, Method}
import org.apache.james.jmap.rfc8621.contract.CustomMethodContract.CUSTOM
import org.apache.james.jmap.rfc8621.contract.DownloadContract.accountId
import org.apache.james.jmap.rfc8621.contract.Fixture._
import org.apache.james.jmap.routes.{Applicable, Blob, BlobResolutionResult, BlobResolver, NonApplicable}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.model.ContentType
import org.apache.james.utils.{DataProbeImpl, GuiceProbe}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.{BeforeEach, Test}
import org.reactivestreams.Publisher
import play.api.libs.json.{JsObject, Json}
import reactor.core.scala.publisher.SMono
import sttp.capabilities.WebSockets
import sttp.client3.monad.IdMonad
import sttp.client3.okhttp.OkHttpSyncBackend
import sttp.client3.{Identity, RequestT, SttpBackend, asWebSocket, basicRequest}
import sttp.model.Uri
import sttp.monad.MonadError
import sttp.monad.syntax.MonadErrorOps
import sttp.ws.WebSocketFrame
import sttp.ws.WebSocketFrame.Text

import scala.util.{Success, Try}

object CustomMethodContract {
  val CUSTOM: CapabilityIdentifier = "urn:apache:james:params:jmap:custom"
  val eventId = EventId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b4")

  private val expected_session_object: String =
    s"""{
      |  "capabilities" : {
      |    "urn:ietf:params:jmap:submission": {
      |      "maxDelayedSend": 0,
      |      "submissionExtensions": {}
      |    },
      |    "urn:ietf:params:jmap:core" : {
      |      "maxSizeUpload" : 20971520,
      |      "maxConcurrentUpload" : 4,
      |      "maxSizeRequest" : 10000000,
      |      "maxConcurrentRequests" : 4,
      |      "maxCallsInRequest" : 16,
      |      "maxObjectsInGet" : 500,
      |      "maxObjectsInSet" : 500,
      |      "collationAlgorithms" : [ "i;unicode-casemap" ]
      |    },
      |    "urn:ietf:params:jmap:mail" : {
      |      "maxMailboxesPerEmail" : 10000000,
      |      "maxMailboxDepth" : null,
      |      "maxSizeMailboxName" : 200,
      |      "maxSizeAttachmentsPerEmail" : 20000000,
      |      "emailQuerySortOptions" : ["receivedAt", "sentAt", "size", "from", "to", "subject"],
      |      "mayCreateTopLevelMailbox" : true
      |    },
      |    "urn:ietf:params:jmap:websocket": {
      |      "supportsPush": true,
      |      "url": "ws://domain.com/jmap/ws"
      |    },
      |    "urn:apache:james:params:jmap:mail:quota": {},
      |    "urn:ietf:params:jmap:quota": {},
      |    "urn:apache:james:params:jmap:mail:identity:sortorder": {},
      |    "urn:apache:james:params:jmap:delegation": {},
      |    "$CUSTOM": {"custom": "property"},
      |    "urn:apache:james:params:jmap:mail:shares": {},
      |    "urn:ietf:params:jmap:vacationresponse":{},
      |    "urn:ietf:params:jmap:mdn":{}
      |  },
      |  "accounts" : {
      |    "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6" : {
      |      "name" : "bob@domain.tld",
      |      "isPersonal" : true,
      |      "isReadOnly" : false,
      |      "accountCapabilities" : {
      |        "urn:ietf:params:jmap:submission": {
      |          "maxDelayedSend": 0,
      |          "submissionExtensions": {}
      |        },
      |        "urn:ietf:params:jmap:websocket": {
      |            "supportsPush": true,
      |            "url": "ws://domain.com/jmap/ws"
      |        },
      |        "urn:ietf:params:jmap:core" : {
      |          "maxSizeUpload" : 20971520,
      |          "maxConcurrentUpload" : 4,
      |          "maxSizeRequest" : 10000000,
      |          "maxConcurrentRequests" : 4,
      |          "maxCallsInRequest" : 16,
      |          "maxObjectsInGet" : 500,
      |          "maxObjectsInSet" : 500,
      |          "collationAlgorithms" : [ "i;unicode-casemap" ]
      |        },
      |        "urn:ietf:params:jmap:mail" : {
      |          "maxMailboxesPerEmail" : 10000000,
      |          "maxMailboxDepth" : null,
      |          "maxSizeMailboxName" : 200,
      |          "maxSizeAttachmentsPerEmail" : 20000000,
      |          "emailQuerySortOptions" : ["receivedAt", "sentAt", "size", "from", "to", "subject"],
      |          "mayCreateTopLevelMailbox" : true
      |        },
      |        "urn:apache:james:params:jmap:mail:quota": {},
      |        "urn:ietf:params:jmap:quota": {},
      |        "urn:apache:james:params:jmap:mail:identity:sortorder": {},
      |        "urn:apache:james:params:jmap:delegation": {},
      |        "urn:apache:james:params:jmap:mail:shares": {},
      |        "$CUSTOM": {"custom": "property"},
      |        "urn:ietf:params:jmap:vacationresponse":{},
      |        "urn:ietf:params:jmap:mdn":{}
      |      }
      |    }
      |  },
      |  "primaryAccounts" : {
      |    "urn:ietf:params:jmap:submission": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
      |    "urn:ietf:params:jmap:websocket": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
      |    "urn:ietf:params:jmap:core" : "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
      |    "urn:ietf:params:jmap:mail" : "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
      |    "urn:apache:james:params:jmap:mail:quota": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
      |    "urn:ietf:params:jmap:quota": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
      |    "urn:apache:james:params:jmap:mail:identity:sortorder": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
      |    "urn:apache:james:params:jmap:delegation": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
      |    "urn:apache:james:params:jmap:mail:shares": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
      |    "$CUSTOM": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
      |    "urn:ietf:params:jmap:vacationresponse": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
      |    "urn:ietf:params:jmap:mdn": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
      |  },
      |  "username" : "bob@domain.tld",
      |  "apiUrl" : "http://domain.com/jmap",
      |  "downloadUrl" : "http://domain.com/download/{accountId}/{blobId}?type={type}&name={name}",
      |  "uploadUrl" : "http://domain.com/upload/{accountId}",
      |  "eventSourceUrl" : "http://domain.com/eventSource?types={types}&closeAfter={closeafter}&ping={ping}",
      |  "state" : "2c9f1b12-b35a-43e6-9af2-0106fb53a943"
      |}""".stripMargin
}

case class CustomCapabilityProperties() extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj(("custom", "property"))
}

case class CustomCapability(properties: CustomCapabilityProperties = CustomCapabilityProperties(), identifier: CapabilityIdentifier = CUSTOM) extends Capability

case object CustomCapabilityFactory extends CapabilityFactory {
  override def create(urlPrefixes: UrlPrefixes): Capability = CustomCapability()

  override def id(): CapabilityIdentifier = CUSTOM
}

class CustomMethodModule extends AbstractModule {
  override protected def configure(): Unit = {
    val supportedCapabilities: Multibinder[CapabilityFactory] = Multibinder.newSetBinder(binder, classOf[CapabilityFactory])
    supportedCapabilities.addBinding.toInstance(CustomCapabilityFactory)
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[CustomMethod])
    Multibinder.newSetBinder(binder(), classOf[TypeName])
      .addBinding()
      .toInstance(CustomTypeName)
    Multibinder.newSetBinder(binder(), classOf[GuiceProbe])
      .addBinding()
      .to(classOf[JmapEventBusProbe])
    Multibinder.newSetBinder(binder(), classOf[BlobResolver])
      .addBinding()
      .to(classOf[CustomBlobResolver])
  }
}

case object CustomBlob extends Blob {
  private val payload: Array[Byte] = "zomeuh".getBytes(StandardCharsets.UTF_8)

  override def blobId: mail.BlobId = org.apache.james.jmap.mail.BlobId("gabouh")

  override def contentType: ContentType = ContentType.of("application/bytes")

  override def size: Try[Size] = Success(refineV[NonNegative](payload.length.toLong).toOption.get)

  override def content: InputStream = new ByteArrayInputStream(payload)
}

class CustomBlobResolver extends BlobResolver {
  override def resolve(blobId: org.apache.james.jmap.mail.BlobId, mailboxSession: MailboxSession): BlobResolutionResult =
    if (blobId.equals(CustomBlob.blobId)) {
      Applicable(SMono.just(CustomBlob))
    } else {
      NonApplicable
    }
}

class CustomMethod extends Method {

  override val methodName = MethodName("Custom/echo")

  override def process(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession): Publisher[InvocationWithContext] = SMono.just(invocation)

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, CUSTOM)
}

object IntState {
  def parse(string: String): Either[IllegalArgumentException, IntState] = Try(Integer.parseInt(string))
    .toEither
    .map(IntState(_))
    .left.map(new IllegalArgumentException(_))
}

case class IntState(i: Int) extends State {
  override def serialize: String = i.toString
}

case object CustomTypeName extends TypeName {
  override val asString: String = "MyTypeName"

  override def parse(string: String): Option[TypeName] = string match {
    case CustomTypeName.asString => Some(CustomTypeName)
    case _ => None
  }

  override def parseState(string: String): Either[IllegalArgumentException, IntState] = IntState.parse(string)
}

class JmapEventBusProbe @Inject() (@Named("JMAP") jmapEventBus: EventBus) extends GuiceProbe {
  def emitStateChange(stateChangeEvent: StateChangeEvent, accountId: AccountId): Unit =
    SMono(jmapEventBus.dispatch(stateChangeEvent, AccountIdRegistrationKey(accountId))).block()
}

trait CustomMethodContract {

  private lazy val backend: SttpBackend[Identity, WebSockets] = OkHttpSyncBackend()
  private lazy implicit val monadError: MonadError[Identity] = IdMonad

  @BeforeEach
  def setUp(server: GuiceJamesServer): Unit = {
    server.getProbe(classOf[DataProbeImpl])
      .fluent()
      .addDomain(DOMAIN.asString())
      .addUser(BOB.asString(), BOB_PASSWORD)

    requestSpecification = baseRequestSpecBuilder(server)
      .setAuth(authScheme(UserCredential(BOB, BOB_PASSWORD)))
      .build
  }

  @Test
  def pushShouldSupportCustomTypeNameAndIntStateWhenDataTypesAreMyTypeName(server: GuiceJamesServer): Unit = {
    val accountId: AccountId = AccountId.fromUsername(BOB)
    val intState = CustomTypeName.parseState("1").toOption.get
    val stateChangeEvent: StateChangeEvent = StateChangeEvent(eventId = CustomMethodContract.eventId, username = BOB, map = Map(CustomTypeName -> intState))
    Thread.sleep(100)

    val response: Either[String, String] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, String] {
          ws =>
            ws.send(WebSocketFrame.text(
              """{
                |  "@type": "WebSocketPushEnable",
                |  "dataTypes": ["MyTypeName"]
                |}""".stripMargin))

            Thread.sleep(100)

            server.getProbe(classOf[JmapEventBusProbe])
              .emitStateChange(stateChangeEvent, accountId)

            ws.receive()
              .map { case t: Text => t.payload }
        })
        .send(backend)
        .body

    Thread.sleep(100)

    assertThatJson(response.toOption.get)
      .isEqualTo("""{
                   |	"@type": "StateChange",
                   |	"changed": {
                   |		"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6": {
                   |			"MyTypeName": "1"
                   |		}
                   |	}
                   |}""".stripMargin)
  }

  @Test
  def pushShouldSupportCustomTypeNameAndIntStateWhenDataTypesAreNull(server: GuiceJamesServer): Unit = {
    val accountId: AccountId = AccountId.fromUsername(BOB)
    val intState = CustomTypeName.parseState("1").toOption.get
    val stateChangeEvent: StateChangeEvent = StateChangeEvent(eventId = CustomMethodContract.eventId, username = BOB, map = Map(CustomTypeName -> intState))
    Thread.sleep(100)

    val response: Either[String, String] =
      authenticatedRequest(server)
        .response(asWebSocket[Identity, String] {
          ws =>
            ws.send(WebSocketFrame.text(
              """{
                |  "@type": "WebSocketPushEnable",
                |  "dataTypes": null
                |}""".stripMargin))

            Thread.sleep(100)

            server.getProbe(classOf[JmapEventBusProbe])
              .emitStateChange(stateChangeEvent, accountId)

            ws.receive()
              .map { case t: Text => t.payload }
        })
        .send(backend)
        .body

    Thread.sleep(100)

    assertThatJson(response.toOption.get)
      .isEqualTo("""{
                   |	"@type": "StateChange",
                   |	"changed": {
                   |		"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6": {
                   |			"MyTypeName": "1"
                   |		}
                   |	}
                   |}""".stripMargin)
  }

  @Test
  def getShouldReturnCorrectSession(): Unit = {
    val sessionJson: String = `given`()
      .when()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .get("/session")
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(sessionJson).isEqualTo(CustomMethodContract.expected_session_object)
  }

  @Test
  def customMethodShouldRespondOKWithRFC8621VersionAndSupportedMethod(): Unit = {
    val response = `given`()
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        """{
          |  "using": [
          |    "urn:ietf:params:jmap:core", "urn:apache:james:params:jmap:custom"
          |  ],
          |  "methodCalls": [
          |    [
          |      "Custom/echo",
          |      {
          |        "arg1": "arg1data",
          |        "arg2": "arg2data"
          |      },
          |      "c1"
          |    ]
          |  ]
          |}""".stripMargin)
    .when()
      .post()
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract()
      .body()
      .asString()

    assertThatJson(response)
      .isEqualTo(
        s"""{
          |  "sessionState": "${SESSION_STATE.value}",
          |  "methodResponses": [
          |    [
          |      "Custom/echo",
          |      {
          |        "arg1": "arg1data",
          |        "arg2": "arg2data"
          |      },
          |      "c1"
          |    ]
          |  ]
          |}""".stripMargin)
  }

  @Test
  def customMethodShouldReturnUnknownMethodWhenMissingCoreCapability(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        """{
          |  "using": [
          |    "urn:apache:james:params:jmap:custom"
          |  ],
          |  "methodCalls": [
          |    [
          |      "Custom/echo",
          |      {
          |        "arg1": "arg1data",
          |        "arg2": "arg2data"
          |      },
          |      "c1"
          |    ]
          |  ]
          |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "error",
         |    {
         |      "type": "unknownMethod",
         |      "description": "Missing capability(ies): urn:ietf:params:jmap:core"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def customMethodShouldReturnUnknownMethodWhenMissingCustomCapability(): Unit = {
    val response = `given`
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .body(
        """{
          |  "using": [
          |    "urn:ietf:params:jmap:core"
          |  ],
          |  "methodCalls": [
          |    [
          |      "Custom/echo",
          |      {
          |        "arg1": "arg1data",
          |        "arg2": "arg2data"
          |      },
          |      "c1"
          |    ]
          |  ]
          |}""".stripMargin)
    .when
      .post
    .`then`
      .statusCode(SC_OK)
      .contentType(JSON)
      .extract
      .body
      .asString

    assertThatJson(response).isEqualTo(
      s"""{
         |  "sessionState": "${SESSION_STATE.value}",
         |  "methodResponses": [[
         |    "error",
         |    {
         |      "type": "unknownMethod",
         |      "description": "Missing capability(ies): urn:apache:james:params:jmap:custom"
         |    },
         |    "c1"]]
         |}""".stripMargin)
  }

  @Test
  def shouldAcceptCustomBlobResolver(): Unit = {
    val response = `given`
      .basePath("")
      .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
    .when
      .get(s"/download/$accountId/gabouh")
    .`then`
      .statusCode(SC_OK)
      .contentType("application/bytes")
      .extract
      .body
      .asString

    assertThat(new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8)))
      .hasContent("zomeuh")
  }

  private def authenticatedRequest(server: GuiceJamesServer): RequestT[Identity, Either[String, String], Any] = {
    val port = server.getProbe(classOf[JmapGuiceProbe])
      .getJmapPort
      .getValue

    basicRequest.get(Uri.apply(new URI(s"ws://127.0.0.1:$port/jmap/ws")))
      .header("Authorization", "Basic Ym9iQGRvbWFpbi50bGQ6Ym9icGFzc3dvcmQ=")
      .header("Accept", ACCEPT_RFC8621_VERSION_HEADER)
  }
}
