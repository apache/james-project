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

package org.apache.james.jmap.http

import java.nio.charset.StandardCharsets
import java.util.Base64

import org.apache.james.core.Username
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class UserCredentialParserTest {
  @Test
  def shouldReturnCredentialWhenUsernamePasswordToken(): Unit = {
    val token: String = "Basic " + toBase64("user1:password")

    assertThat(UserCredential.parseUserCredentials(token))
      .isEqualTo(Some(UserCredential(Username.of("user1"), "password")))
  }

  @Test
  def shouldAcceptPartSeparatorAsPartOfPassword(): Unit = {
    val token: String = "Basic " + toBase64("user1:pass:word")

    assertThat(UserCredential.parseUserCredentials(token))
      .isEqualTo(Some(UserCredential(Username.of("user1"), "pass:word")))
  }

  @Test
  def shouldReturnCredentialWhenRandomSpecialCharacterInUsernameToken(): Unit = {
    val token: String = "Basic " + toBase64("fd2*#jk:password")

    assertThat(UserCredential.parseUserCredentials(token))
      .isEqualTo(Some(UserCredential(Username.of("fd2*#jk"), "password")))
  }

  @Test
  def shouldReturnCredentialsWhenRandomSpecialCharacterInBothUsernamePasswoedToken(): Unit = {
    val token: String = "Basic " + toBase64("fd2*#jk:password@fd23*&^$%")

    assertThat(UserCredential.parseUserCredentials(token))
      .isEqualTo(Some(UserCredential(Username.of("fd2*#jk"), "password@fd23*&^$%")))
  }

  @Test
  def shouldReturnCredentialWhenUsernameDomainPasswordToken(): Unit = {
    val token: String = "Basic " + toBase64("user1@domain.tld:password")

    assertThat(UserCredential.parseUserCredentials(token))
      .isEqualTo(Some(UserCredential(Username.of("user1@domain.tld"), "password")))
  }

  @Test
  def shouldReturnCredentialWhenUsernameDomainNoPasswordToken(): Unit = {
    val token: String = "Basic " + toBase64("user1@domain.tld:")

    assertThat(UserCredential.parseUserCredentials(token))
      .isEqualTo(Some(UserCredential(Username.of("user1@domain.tld"), "")))
  }

  @Test
  def shouldReturnNoneWhenPayloadIsNotBase64(): Unit = {
    val token: String = "Basic user1:password"

    assertThat(UserCredential.parseUserCredentials(token))
      .isEqualTo(None)
  }

  @Test
  def shouldReturnNoneWhenEmptyToken(): Unit = {
    assertThat(UserCredential.parseUserCredentials(""))
      .isEqualTo(None)
  }

  @Test
  def shouldThrowWhenWrongFormatCredential(): Unit = {
    val token: String = "Basic " + toBase64("user1@password")

    assertThrows(classOf[UnauthorizedException], () => UserCredential.parseUserCredentials(token))
  }

  @Test
  def shouldReturnNoneWhenUpperCaseToken(): Unit = {
    val token: String = "BASIC " + toBase64("user1@password")

    assertThat(UserCredential.parseUserCredentials(token))
      .isEqualTo(None)
  }

  @Test
  def shouldReturnNoneWhenLowerCaseToken(): Unit = {
    val token: String = "basic " + toBase64("user1:password")

    assertThat(UserCredential.parseUserCredentials(token))
      .isEqualTo(None)
  }

  @Test
  def shouldReturnNoneWhenCredentialWithNoPassword(): Unit = {
    val token: String = "Basic " + toBase64("user1:")

    assertThat(UserCredential.parseUserCredentials(token))
      .isEqualTo(Some(UserCredential(Username.of("user1"), "")))
  }

  @Test
  def shouldThrowWhenCredentialWithNoUsername(): Unit = {
    val token: String = "Basic " + toBase64(":pass")

    assertThrows(classOf[UnauthorizedException], () => UserCredential.parseUserCredentials(token))
  }

  private def toBase64(stringValue: String): String = {
    Base64.getEncoder.encodeToString(stringValue.getBytes(StandardCharsets.UTF_8))
  }
}
