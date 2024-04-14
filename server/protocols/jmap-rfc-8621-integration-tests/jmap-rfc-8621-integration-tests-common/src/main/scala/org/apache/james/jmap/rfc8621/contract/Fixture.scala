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
import java.nio.charset.StandardCharsets.UTF_8
import java.util.{Base64, UUID}

import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.authentication.PreemptiveBasicAuthScheme
import io.restassured.builder.RequestSpecBuilder
import io.restassured.config.EncoderConfig.encoderConfig
import io.restassured.config.RestAssuredConfig.newConfig
import io.restassured.http.{ContentType, Header, Headers}
import org.apache.james.GuiceJamesServer
import org.apache.james.core.{Domain, Username}
import org.apache.james.jmap.JMAPUrls.JMAP
import org.apache.james.jmap.JmapGuiceProbe
import org.apache.james.jmap.core.ResponseObject.SESSION_STATE
import org.apache.james.jmap.http.UserCredential
import org.apache.james.mime4j.dom.Message

object Fixture {
  val ACCOUNT_ID: String = "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6"
  val ALICE_ACCOUNT_ID: String = "2bd806c97f0e00af1a1fc3328fa763a9269723c8db8fac4f93af71db186d6e90"
  val ANDRE_ACCOUNT_ID: String = "1e8584548eca20f26faf6becc1704a0f352839f12c208a47fbd486d60f491f7c"
  val DAVID_ACCOUNT_ID: String = "a63dc794489dca3a428ae19c0632425619aa2d8551cd8dab26f4b9a87c774342"


  def createTestMessage: Message = Message.Builder
      .of
      .setSubject("test")
      .setSender(ANDRE.asString())
      .setFrom(ANDRE.asString())
      .setSubject("World domination \r\n" +
        " and this is also part of the header")
      .setBody("testmail", StandardCharsets.UTF_8)
      .build

  def baseRequestSpecBuilder(server: GuiceJamesServer) = new RequestSpecBuilder()
    .setContentType(ContentType.JSON)
    .setAccept(ContentType.JSON)
    .setConfig(newConfig.encoderConfig(encoderConfig.defaultContentCharset(StandardCharsets.UTF_8)))
    .setPort(server.getProbe(classOf[JmapGuiceProbe])
      .getJmapPort
      .getValue)
    .setBasePath(JMAP)

  def authScheme(userCredential: UserCredential): PreemptiveBasicAuthScheme = {
    val authScheme: PreemptiveBasicAuthScheme = new PreemptiveBasicAuthScheme
    authScheme.setUserName(userCredential.username.asString())
    authScheme.setPassword(userCredential.password)

    authScheme
  }

  def toBase64(stringValue: String): String = {
    Base64.getEncoder.encodeToString(stringValue.getBytes(UTF_8))
  }

  def getHeadersWith(authHeader: Header): Headers = {
    new Headers(
      new Header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER),
      authHeader
    )
  }

  val AUTHORIZATION_HEADER: String = "Authorization"
  val DOMAIN: Domain = Domain.of("domain.tld")
  val DOMAIN_WITH_SPACE: String = "dom ain.tld"
  val _2_DOT_DOMAIN: Domain = Domain.of("do.main.tld")
  val BOB: Username = Username.fromLocalPartWithDomain("bob", DOMAIN)
  val ANDRE: Username = Username.fromLocalPartWithDomain("andre", DOMAIN)
  val CEDRIC: Username = Username.fromLocalPartWithDomain("cedric", DOMAIN)
  val DAVID: Username = Username.fromLocalPartWithDomain("david", DOMAIN)
  val ALICE: Username = Username.fromLocalPartWithDomain("alice", _2_DOT_DOMAIN)
  val DAVID_IDENTITY_ID: String = UUID.nameUUIDFromBytes(ANDRE.asString().getBytes(StandardCharsets.UTF_8)).toString
  val ANDRE_IDENTITY_ID: String = "1d684cf0-101b-300b-82c0-8e17f8d464bc"
  val IDENTITY_ID: String = UUID.nameUUIDFromBytes(BOB.asString().getBytes(StandardCharsets.UTF_8)).toString
  val BOB_PASSWORD: String = "bobpassword"
  val ANDRE_PASSWORD: String = "andrepassword"
  val ALICE_PASSWORD: String = "alicepassword"


  val BOB_BASIC_AUTH_HEADER: Header = new Header(AUTHORIZATION_HEADER, s"Basic ${toBase64(s"${BOB.asString}:$BOB_PASSWORD")}")

  val ECHO_REQUEST_OBJECT: String =
    """{
      |  "using": [
      |    "urn:ietf:params:jmap:core"
      |  ],
      |  "methodCalls": [
      |    [
      |      "Core/echo",
      |      {
      |        "arg1": "arg1data",
      |        "arg2": "arg2data"
      |      },
      |      "c1"
      |    ]
      |  ]
      |}""".stripMargin

  val ECHO_REQUEST_OBJECT_WITHOUT_CORE_CAPABILITY: String =
    """{
      |  "using": [],
      |  "methodCalls": [
      |    [
      |      "Core/echo",
      |      {
      |        "arg1": "arg1data",
      |        "arg2": "arg2data"
      |      },
      |      "c1"
      |    ]
      |  ]
      |}""".stripMargin

  val ECHO_RESPONSE_OBJECT: String =
    s"""{
      |  "sessionState": "${SESSION_STATE.value}",
      |  "methodResponses": [
      |    [
      |      "Core/echo",
      |      {
      |        "arg1": "arg1data",
      |        "arg2": "arg2data"
      |      },
      |      "c1"
      |    ]
      |  ]
      |}""".stripMargin

  val ACCEPT_RFC8621_VERSION_HEADER: String = "application/json; jmapVersion=rfc-8621"
  val RFC8621_VERSION_HEADER: String = "jmapVersion=rfc-8621"

  val USER: Username = Username.fromLocalPartWithDomain("user", DOMAIN)
  val USER_PASSWORD: String = "user"

  // These tokens copied from class JwtTokenVerifierTest
  val USER_TOKEN: String =
    "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyQGRvbWFpbi50bGQifQ.U-dUPv6OU6KO5N7CooHUfMkCd" +
      "FJHx2F3H4fm7Q79g1BPfBSkifPj5xyVlZ0JwEGXypC4zBw9ay3l4DxzX7D_6p1Hx_ihXsoLx1Ca-WUo44x-XRSpPfgxiZjHCJkGBLMV3RZlA" +
      "jip-d18mxkcX3JGplX_sCQkFisduAOAHuKSUg9wI6VBgUQi_0B35FYv6tP_bD6eFtvaAUN9QyXXh8UQjEp8CO12lRz6enfLx_V6BG_fEMkee" +
      "6vRqdEqx_F9OF3eWTe1giMp_JhQ7_l1OXXtbd4TndVvTeuVy4irPbsRc-M8x_-qTDpFp6saRRsyOcFspxPp5n3yIhEK7B3UZiseXw"

  val UNKNOWN_USER_TOKEN: String =
    "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIn0.T04BTk" +
      "LXkJj24coSZkK13RfG25lpvmSl2MJ7N10KpBk9_-95EGYZdog-BDAn3PJzqVw52z-Bwjh4VOj1-j7cURu0cT4jXehhUrlCxS4n7QHZD" +
      "N_bsEYGu7KzjWTpTsUiHe-rN7izXVFxDGG1TGwlmBCBnPW-EFCf9ylUsJi0r2BKNdaaPRfMIrHptH1zJBkkUziWpBN1RNLjmvlAUf49" +
      "t1Tbv21ZqYM5Ht2vrhJWczFbuC-TD-8zJkXhjTmA1GVgomIX5dx1cH-dZX1wANNmshUJGHgepWlPU-5VIYxPEhb219RMLJIELMY2qN" +
      "OR8Q31ydinyqzXvCSzVJOf6T60-w"

  val INVALID_JWT_TOKEN: String =
    "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhZG1pbkBvcGVu" +
      "thispartiscompletelywrong.reQc3DiVvbQHF08oW1qOUyDJyv3tfzDNk8jhVZequiCdOI9vXnRlOe" +
      "-yDYktd4WT8MYhqY7MgS-wR0vO9jZFv8ZCgd_MkKCvCO0HmMjP5iQPZ0kqGkgWUH7X123tfR38MfbCVAdPDba-K3MfkogV1xvDhlkPScFr_6MxE" +
      "xtedOK2JnQZn7t9sUzSrcyjWverm7gZkPptkIVoS8TsEeMMME5vFXe_nqkEG69q3kuBUm_33tbR5oNS0ZGZKlG9r41lHBjyf9J1xN4UYV8n866d" +
      "a7RPPCzshIWUtO0q9T2umWTnp-6OnOdBCkndrZmRR6pPxsD5YL0_77Wq8KT_5__fGA"

  val GET_ALL_MAILBOXES_REQUEST: String =
    """{
      |  "using": [
      |    "urn:ietf:params:jmap:core",
      |    "urn:ietf:params:jmap:mail"],
      |  "methodCalls": [[
      |      "Mailbox/get",
      |      {
      |        "accountId": "29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6",
      |        "ids": null
      |      },
      |      "c1"]]
      |}""".stripMargin
}
