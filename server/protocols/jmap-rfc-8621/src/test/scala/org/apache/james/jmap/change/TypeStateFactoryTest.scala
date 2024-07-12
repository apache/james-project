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
 * ************************************************************** */

package org.apache.james.jmap.change

import org.apache.james.jmap.api.change.TypeStateFactory
import org.apache.james.jmap.api.model.TypeName
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters._

class TypeStateFactoryTest {
  val ALL: Set[TypeName] = Set(EmailTypeName, MailboxTypeName, ThreadTypeName, IdentityTypeName, EmailSubmissionTypeName, EmailDeliveryTypeName, VacationResponseTypeName)
  val factory: TypeStateFactory = TypeStateFactory(ALL.asJava)

  @Test
  def strictParseEmailTypeNameStringShouldReturnRightEmailTypeName(): Unit =
    assertThat(factory.strictParse("Email")).isEqualTo(Right(EmailTypeName))

  @Test
  def strictParseMailboxTypeNameStringShouldReturnRightMailboxTypeName(): Unit =
    assertThat(factory.strictParse("Mailbox")).isEqualTo(Right(MailboxTypeName))

  @Test
  def strictParseThreadTypeNameStringShouldReturnRightThreadTypeName(): Unit =
    assertThat(factory.strictParse("Thread")).isEqualTo(Right(ThreadTypeName))

  @Test
  def strictParseIdentityTypeNameStringShouldReturnRightIdentityTypeName(): Unit =
    assertThat(factory.strictParse("Identity")).isEqualTo(Right(IdentityTypeName))

  @Test
  def strictParseEmailSubmissionTypeNameStringShouldReturnRightEmailSubmissionTypeName(): Unit =
    assertThat(factory.strictParse("EmailSubmission")).isEqualTo(Right(EmailSubmissionTypeName))

  @Test
  def strictParseEmailDeliveryTypeNameStringShouldReturnRightEmailDeliveryTypeName(): Unit =
    assertThat(factory.strictParse("EmailDelivery")).isEqualTo(Right(EmailDeliveryTypeName))

  @Test
  def strictParseVacationResponseTypeNameStringShouldReturnRightVacationResponseTypeName(): Unit =
    assertThat(factory.strictParse("VacationResponse")).isEqualTo(Right(VacationResponseTypeName))

  @Test
  def strictParseWrongTypeNameStringShouldReturnLeft(): Unit =
    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(factory.strictParse("email").isLeft).isTrue
      softly.assertThat(factory.strictParse("mailbox").isLeft).isTrue
      softly.assertThat(factory.strictParse("thread").isLeft).isTrue
      softly.assertThat(factory.strictParse("identity").isLeft).isTrue
      softly.assertThat(factory.strictParse("emailSubmission").isLeft).isTrue
      softly.assertThat(factory.strictParse("emailDelivery").isLeft).isTrue
      softly.assertThat(factory.strictParse("filter").isLeft).isTrue
      softly.assertThat(factory.strictParse("vacationResponse").isLeft).isTrue
    })

  @Test
  def lenientParseValidTypeNameStringShouldReturnTypeName(): Unit = {
    assertThat(factory.lenientParse("Email")).isEqualTo(Some(EmailTypeName))
    assertThat(factory.lenientParse("Mailbox")).isEqualTo(Some(MailboxTypeName))
    assertThat(factory.lenientParse("Thread")).isEqualTo(Some(ThreadTypeName))
    assertThat(factory.lenientParse("Identity")).isEqualTo(Some(IdentityTypeName))
    assertThat(factory.lenientParse("EmailSubmission")).isEqualTo(Some(EmailSubmissionTypeName))
    assertThat(factory.lenientParse("EmailDelivery")).isEqualTo(Some(EmailDeliveryTypeName))
    assertThat(factory.lenientParse("VacationResponse")).isEqualTo(Some(VacationResponseTypeName))
  }

  @Test
  def lenientParseWrongTypeNameStringShouldReturnEmpty(): Unit =
    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(factory.lenientParse("email").isEmpty).isTrue
      softly.assertThat(factory.lenientParse("mailbox").isEmpty).isTrue
      softly.assertThat(factory.lenientParse("thread").isEmpty).isTrue
      softly.assertThat(factory.lenientParse("identity").isEmpty).isTrue
      softly.assertThat(factory.lenientParse("emailSubmission").isEmpty).isTrue
      softly.assertThat(factory.lenientParse("emailDelivery").isEmpty).isTrue
      softly.assertThat(factory.lenientParse("filter").isEmpty).isTrue
      softly.assertThat(factory.lenientParse("vacationResponse").isEmpty).isTrue
    })
}
