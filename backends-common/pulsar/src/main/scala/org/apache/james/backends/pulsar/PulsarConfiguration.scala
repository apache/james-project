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
package org.apache.james.backends.pulsar

import java.net.{URI, URISyntaxException}
import org.apache.commons.configuration2.Configuration
import com.google.common.base.Strings
import com.sksamuel.pulsar4s.{PulsarAsyncClient, PulsarClient, PulsarClientConfig}
import org.apache.pulsar.client.admin.PulsarAdmin
import org.apache.pulsar.client.impl.auth.{AuthenticationBasic, AuthenticationDisabled, AuthenticationToken}

import scala.jdk.CollectionConverters.MapHasAsJava

object PulsarConfiguration {
  val BROKER_URI_PROPERTY_NAME = "broker.uri"
  val ADMIN_URI_PROPERTY_NAME = "admin.uri"
  val NAMESPACE_PROPERTY_NAME = "namespace"
  val AUTHENTICATION_TYPE_PROPERTY_NAME = "authentication.type"
  val AUTHENTICATION_TYPE_NO_AUTH = "no-auth"
  val AUTHENTICATION_TYPE_AUTH_TOKEN = "token"
  val AUTHENTICATION_TYPE_AUTH_BASIC = "basic"
  val AUTHENTICATION_TOKEN_PROPERTY_NAME = "authentication.token"
  val AUTHENTICATION_BASIC_USERID_PROPERTY_NAME = "authentication.basic.userId"
  val AUTHENTICATION_BASIC_PASSWORD_PROPERTY_NAME = "authentication.basic.password"


  def from(configuration: Configuration): PulsarConfiguration = {
    val brokerUri: String = extractUri(configuration, BROKER_URI_PROPERTY_NAME)
    val adminUri: String = extractUri(configuration, ADMIN_URI_PROPERTY_NAME)
    val authTypeString: String = configuration.getString(AUTHENTICATION_TYPE_PROPERTY_NAME, AUTHENTICATION_TYPE_NO_AUTH);
    val auth = authTypeString match {
      case AUTHENTICATION_TYPE_NO_AUTH => Auth.NoAuth

      case AUTHENTICATION_TYPE_AUTH_TOKEN =>
        val token = configuration.getString(AUTHENTICATION_TOKEN_PROPERTY_NAME)
        if (Strings.isNullOrEmpty(token))
          throw new IllegalStateException(s"You need to specify a non-empty value for ${AUTHENTICATION_TOKEN_PROPERTY_NAME}")
        Auth.Token(token)

      case AUTHENTICATION_TYPE_AUTH_BASIC =>
        val userId = configuration.getString(AUTHENTICATION_BASIC_USERID_PROPERTY_NAME)
        if (Strings.isNullOrEmpty(userId))
          throw new IllegalStateException(s"You need to specify a non-empty value for ${AUTHENTICATION_BASIC_USERID_PROPERTY_NAME}")
        val password = configuration.getString(AUTHENTICATION_BASIC_PASSWORD_PROPERTY_NAME)
        if (Strings.isNullOrEmpty(password))
          throw new IllegalStateException(s"You need to specify a non-empty value for ${AUTHENTICATION_BASIC_PASSWORD_PROPERTY_NAME}")
        Auth.Basic(userId, password)

      case _ =>
        throw new NotImplementedError(s"Authentication type $authTypeString is not implemented")
    }

    val namespace = configuration.getString(NAMESPACE_PROPERTY_NAME)
    if (Strings.isNullOrEmpty(namespace))
      throw new IllegalStateException(s"You need to specify the pulsar namespace as ${NAMESPACE_PROPERTY_NAME}")
    new PulsarConfiguration(brokerUri, adminUri, Namespace(namespace), auth)
  }

  private def extractUri(configuration: Configuration, uriPropertyName: String): String = {
    val extractedUri = configuration.getString(uriPropertyName)
    if (Strings.isNullOrEmpty(extractedUri))
      throw new IllegalStateException(s"You need to specify the pulsar $uriPropertyName uri")
    try {
      new URI(extractedUri)
    } catch {
      case ex: URISyntaxException =>
        throw new IllegalStateException(s"'$extractedUri' is not a valid $uriPropertyName uri", ex)
    }
    extractedUri
  }
}

case class Namespace(asString: String)

sealed trait Auth

object Auth {
  def noAuth() = NoAuth

  case object NoAuth extends Auth

  def token(value: String) = Token(value)

  case class Token(value: String) extends Auth

  def basic(userId: String, password: String) = Basic(userId, password)

  case class Basic(userId: String, password: String) extends Auth
}

case class PulsarConfiguration(brokerUri: String, adminUri: String, namespace: Namespace, auth: Auth = Auth.NoAuth) {
  private val pulsarAuth = auth match {
    case Auth.NoAuth => new AuthenticationDisabled()
    case Auth.Token(value) => new AuthenticationToken(value)
    case Auth.Basic(userId, password) =>
      val basic = new AuthenticationBasic()
      basic.configure(Map("userId" -> userId, "password" -> password).asJava)
      basic
  }

  lazy val adminClient: PulsarAdmin =
    PulsarAdmin.builder()
      .serviceHttpUrl(adminUri)
      .authentication(pulsarAuth)
      .build()

  lazy val asyncClient: PulsarAsyncClient =
    PulsarClient(
      PulsarClientConfig(
        serviceUrl = brokerUri,
        authentication = Some(pulsarAuth)
      )
    )
}