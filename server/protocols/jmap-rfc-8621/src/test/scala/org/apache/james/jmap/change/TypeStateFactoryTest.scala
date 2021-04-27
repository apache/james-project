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

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters._

class TypeStateFactoryTest {
  val ALL: Set[TypeName] = Set(EmailTypeName, MailboxTypeName, ThreadTypeName, IdentityTypeName, EmailSubmissionTypeName, EmailDeliveryTypeName, VacationResponseTypeName)
  val factory: TypeStateFactory = TypeStateFactory(ALL.asJava)

  @Test
  def parseEmailTypeNameStringShouldReturnRightEmailTypeName(): Unit =
    assertThat(factory.parse("Email")).isEqualTo(Right(EmailTypeName))

  @Test
  def parseMailboxTypeNameStringShouldReturnRightMailboxTypeName(): Unit =
    assertThat(factory.parse("Mailbox")).isEqualTo(Right(MailboxTypeName))

  @Test
  def parseThreadTypeNameStringShouldReturnRightThreadTypeName(): Unit =
    assertThat(factory.parse("Thread")).isEqualTo(Right(ThreadTypeName))

  @Test
  def parseIdentityTypeNameStringShouldReturnRightIdentityTypeName(): Unit =
    assertThat(factory.parse("Identity")).isEqualTo(Right(IdentityTypeName))

  @Test
  def parseEmailSubmissionTypeNameStringShouldReturnRightEmailSubmissionTypeName(): Unit =
    assertThat(factory.parse("EmailSubmission")).isEqualTo(Right(EmailSubmissionTypeName))

  @Test
  def parseEmailDeliveryTypeNameStringShouldReturnRightEmailDeliveryTypeName(): Unit =
    assertThat(factory.parse("EmailDelivery")).isEqualTo(Right(EmailDeliveryTypeName))

  @Test
  def parseVacationResponseTypeNameStringShouldReturnRightVacationResponseTypeName(): Unit =
    assertThat(factory.parse("VacationResponse")).isEqualTo(Right(VacationResponseTypeName))

  @Test
  def parseWrongTypeNameStringShouldReturnLeft(): Unit =
    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(factory.parse("email").isLeft).isTrue
      softly.assertThat(factory.parse("mailbox").isLeft).isTrue
      softly.assertThat(factory.parse("thread").isLeft).isTrue
      softly.assertThat(factory.parse("identity").isLeft).isTrue
      softly.assertThat(factory.parse("emailSubmission").isLeft).isTrue
      softly.assertThat(factory.parse("emailDelivery").isLeft).isTrue
      softly.assertThat(factory.parse("filter").isLeft).isTrue
      softly.assertThat(factory.parse("vacationResponse").isLeft).isTrue
    })

}
