/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ***************************************************************/
package org.apache.james.jmap.routes

import java.nio.charset.StandardCharsets

import com.google.common.collect.ImmutableSet
import io.netty.handler.codec.http.HttpHeaderNames.ACCEPT
import io.restassured.RestAssured
import io.restassured.builder.RequestSpecBuilder
import io.restassured.config.EncoderConfig.encoderConfig
import io.restassured.config.RestAssuredConfig.newConfig
import io.restassured.http.ContentType
import org.apache.http.HttpStatus
import org.apache.james.jmap.JMAPUrls.JMAP
import org.apache.james.jmap.json.Fixture._
import org.apache.james.jmap.json.Serializer
import org.apache.james.jmap.model.RequestObject
import org.apache.james.jmap.{JMAPConfiguration, JMAPRoutesHandler, JMAPServer, Version}
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class JMAPApiRoutesTest extends AnyFlatSpec with BeforeAndAfter with Matchers {

  private val TEST_CONFIGURATION: JMAPConfiguration = JMAPConfiguration.builder().enable().randomPort().build()
  private val ACCEPT_JMAP_VERSION_HEADER = "application/json; jmapVersion="
  private val ACCEPT_DRAFT_VERSION_HEADER = ACCEPT_JMAP_VERSION_HEADER + Version.DRAFT.getVersion
  private val ACCEPT_RFC8621_VERSION_HEADER = ACCEPT_JMAP_VERSION_HEADER + Version.RFC8621.getVersion

  private val JMAP_API_ROUTE: JMAPApiRoutes = new JMAPApiRoutes()
  private val ROUTES_HANDLER: ImmutableSet[JMAPRoutesHandler] = ImmutableSet.of(new JMAPRoutesHandler(Version.RFC8621, JMAP_API_ROUTE))

  private val REQUEST_OBJECT: String =
    new Serializer().serialize(RequestObject(Seq(coreIdentifier), Seq(invocation1))).toString()

  private val REQUEST_OBJECT_WITH_UNSUPPORTED_METHOD: String =
    new Serializer().serialize(RequestObject(Seq(coreIdentifier), Seq(invocation1, unsupportedInvocation))).toString()

  private val RESPONSE_OBJECT: String = new Serializer().serialize(responseObject1).toString()
  private val RESPONSE_OBJECT_WITH_UNSUPPORTED_METHOD: String = new Serializer().serialize(responseObjectWithUnsupportedMethod).toString()

  var jmapServer: JMAPServer = _

  before {
    jmapServer = new JMAPServer(TEST_CONFIGURATION, ROUTES_HANDLER)
    jmapServer.start()

    RestAssured.requestSpecification = new RequestSpecBuilder()
      .setContentType(ContentType.JSON)
      .setAccept(ContentType.JSON)
      .setConfig(newConfig.encoderConfig(encoderConfig.defaultContentCharset(StandardCharsets.UTF_8)))
      .setPort(jmapServer.getPort.getValue)
      .setBasePath(JMAP)
      .build
  }

  after {
    jmapServer.stop()
  }

  "RFC-8621 version, GET" should "not supported and return 404 status" in {
    RestAssured
      .`given`()
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .when()
        .get
      .then
        .statusCode(HttpStatus.SC_NOT_FOUND)
  }

  "RFC-8621 version, POST, without body" should "return 200 status" in {
    RestAssured
      .`given`()
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
      .when()
        .post
      .then
        .statusCode(HttpStatus.SC_OK)
  }

  "RFC-8621 version, POST, methods include supported" should "return OK status" in {
    val response = RestAssured
      .`given`()
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(REQUEST_OBJECT)
      .when()
        .post()
      .then
        .statusCode(HttpStatus.SC_OK)
        .contentType(ContentType.JSON)
      .extract()
        .body()
        .asString()

    response shouldBe (RESPONSE_OBJECT)
  }

  "RFC-8621 version, POST, with methods" should "return OK status, ResponseObject depend on method" in {

    val response = RestAssured
      .`given`()
        .header(ACCEPT.toString, ACCEPT_RFC8621_VERSION_HEADER)
        .body(REQUEST_OBJECT_WITH_UNSUPPORTED_METHOD)
      .when()
        .post()
      .then
        .statusCode(HttpStatus.SC_OK)
        .contentType(ContentType.JSON)
      .extract()
        .body()
        .asString()

    response shouldBe (RESPONSE_OBJECT_WITH_UNSUPPORTED_METHOD)
  }

  "Draft version, GET" should "return 404 status" in {
    RestAssured
      .`given`()
        .header(ACCEPT.toString, ACCEPT_DRAFT_VERSION_HEADER)
      .when()
        .get
      .then
        .statusCode(HttpStatus.SC_NOT_FOUND)
  }

  "Draft version, POST, without body" should "return 400 status" in {
    RestAssured
      .`given`()
        .header(ACCEPT.toString, ACCEPT_DRAFT_VERSION_HEADER)
      .when()
        .post
      .then
        .statusCode(HttpStatus.SC_NOT_FOUND)
  }
}
