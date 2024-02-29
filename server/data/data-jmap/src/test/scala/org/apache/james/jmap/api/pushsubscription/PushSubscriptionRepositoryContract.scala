/** ****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one     *
 * or more contributor license agreements.  See the NOTICE file   *
 * distributed with this work for additional information          *
 * regarding copyright ownership.  The ASF licenses this file     *
 * to you under the Apache License, Version 2.0 (the              *
 * "License"); you may not use this file except in compliance     *
 * with the License.  You may obtain a copy of the License at     *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                     *
 * *
 * Unless required by applicable law or agreed to in writing,     *
 * software distributed under the License is distributed on an    *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY         *
 * KIND, either express or implied.  See the License for the      *
 * specific language governing permissions and limitations        *
 * under the License.                                             *
 * **************************************************************** */

package org.apache.james.jmap.api.pushsubscription

import java.net.URL
import java.time.{Clock, Instant, ZoneId, ZonedDateTime}

import org.apache.james.core.Username
import org.apache.james.jmap.api.model.{DeviceClientId, DeviceClientIdInvalidException, ExpireTimeInvalidException, InvalidPushSubscriptionKeys, PushSubscription, PushSubscriptionCreationRequest, PushSubscriptionExpiredTime, PushSubscriptionId, PushSubscriptionKeys, PushSubscriptionNotFoundException, PushSubscriptionServerURL, State, TypeName}
import org.apache.james.jmap.api.pushsubscription.PushSubscriptionRepositoryContract.{ALICE, INVALID_EXPIRE, MAX_EXPIRE, VALID_EXPIRE}
import org.apache.james.utils.UpdatableTickingClock
import org.assertj.core.api.Assertions.{assertThat, assertThatCode, assertThatThrownBy}
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

case object CustomTypeName1 extends TypeName {
  override val asString: String = "custom1"

  override def parse(string: String): Option[TypeName] = string match {
    case CustomTypeName1.asString => Some(CustomTypeName1)
    case _ => None
  }

  override def parseState(string: String): Either[IllegalArgumentException, CustomState] = Right(CustomState(string))
}

case object CustomTypeName2 extends TypeName {
  override val asString: String = "custom2"

  override def parse(string: String): Option[TypeName] = string match {
    case CustomTypeName2.asString => Some(CustomTypeName2)
    case _ => None
  }

  override def parseState(string: String): Either[IllegalArgumentException, CustomState] = Right(CustomState(string))
}

case class CustomState(value: String) extends State {
  override def serialize: String = value
}

object PushSubscriptionRepositoryContract {
  val TYPE_NAME_SET: Set[TypeName] = Set(CustomTypeName1, CustomTypeName2)
  val NOW: Instant = Instant.parse("2021-10-25T07:05:39.160Z")
  val ZONE_ID: ZoneId = ZoneId.of("UTC")
  val CLOCK: Clock = Clock.fixed(NOW, ZONE_ID)
  val INVALID_EXPIRE: ZonedDateTime = ZonedDateTime.now(CLOCK).minusDays(10)
  val VALID_EXPIRE: ZonedDateTime = ZonedDateTime.now(CLOCK).plusDays(2)
  val MAX_EXPIRE: ZonedDateTime = ZonedDateTime.now(CLOCK).plusDays(7)
  val ALICE: Username = Username.of("alice")
}

trait PushSubscriptionRepositoryContract {
  def clock: UpdatableTickingClock
  def testee: PushSubscriptionRepository

  @Test
  def validSubscriptionShouldBeSavedSuccessfully(): Unit = {
    val validRequest = PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      url = PushSubscriptionServerURL(new URL("https://example.com/push")),
      types = Seq(CustomTypeName1))
    val pushSubscriptionId = SMono.fromPublisher(testee.save(ALICE, validRequest)).block().id
    val singleRecordSaved = SFlux.fromPublisher(testee.get(ALICE, Set(pushSubscriptionId).asJava)).count().block()

    assertThat(singleRecordSaved).isEqualTo(1)
  }

  @Test
  def newSavedSubscriptionShouldNotBeValidated(): Unit = {
    val validRequest = PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      url = PushSubscriptionServerURL(new URL("https://example.com/push")),
      types = Seq(CustomTypeName1))
    val pushSubscriptionId = SMono.fromPublisher(testee.save(ALICE, validRequest)).block().id
    val newSavedSubscription = SFlux.fromPublisher(testee.get(ALICE, Set(pushSubscriptionId).asJava)).blockFirst().get

    assertThat(newSavedSubscription.validated).isEqualTo(false)
  }

  @Test
  def subscriptionWithExpireBiggerThanMaxExpireShouldBeSetToMaxExpire(): Unit = {
    val request = PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      url = PushSubscriptionServerURL(new URL("https://example.com/push")),
      expires = Some(PushSubscriptionExpiredTime(VALID_EXPIRE.plusDays(8))),
      types = Seq(CustomTypeName1))
    val pushSubscriptionId = SMono.fromPublisher(testee.save(ALICE, request)).block().id
    val newSavedSubscription = SFlux.fromPublisher(testee.get(ALICE, Set(pushSubscriptionId).asJava)).blockFirst().get

    assertThat(newSavedSubscription.expires.value).isEqualTo(MAX_EXPIRE)
  }

  @Test
  def subscriptionWithInvalidExpireTimeShouldThrowException(): Unit = {
    val invalidRequest = PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      url = PushSubscriptionServerURL(new URL("https://example.com/push")),
      expires = Some(PushSubscriptionExpiredTime(INVALID_EXPIRE)),
      types = Seq(CustomTypeName1))

    assertThatThrownBy(() => SMono.fromPublisher(testee.save(ALICE, invalidRequest)).block())
      .isInstanceOf(classOf[ExpireTimeInvalidException])
  }

  @Test
  def subscriptionWithDuplicatedDeviceClientIdShouldThrowException(): Unit = {
    val firstRequest = PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      url = PushSubscriptionServerURL(new URL("https://example.com/push")),
      types = Seq(CustomTypeName1))
    SMono.fromPublisher(testee.save(ALICE, firstRequest)).block()

    val secondRequestWithDuplicatedDeviceClientId = PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      url = PushSubscriptionServerURL(new URL("https://example.com/push")),
      types = Seq(CustomTypeName1))

    assertThatThrownBy(() => SMono.fromPublisher(testee.save(ALICE, secondRequestWithDuplicatedDeviceClientId)).block())
      .isInstanceOf(classOf[DeviceClientIdInvalidException])
  }

  @Test
  def updateWithOutdatedExpiresShouldThrowException(): Unit = {
    val validRequest = PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      url = PushSubscriptionServerURL(new URL("https://example.com/push")),
      types = Seq(CustomTypeName1))
    val pushSubscriptionId = SMono.fromPublisher(testee.save(ALICE, validRequest)).block().id

    assertThatThrownBy(() => SMono.fromPublisher(testee.updateExpireTime(ALICE, pushSubscriptionId, INVALID_EXPIRE)).block())
      .isInstanceOf(classOf[ExpireTimeInvalidException])
  }

  @Test
  def updateWithExpiresBiggerThanMaxExpiresShouldBeSetToMaxExpires(): Unit = {
    val validRequest = PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      url = PushSubscriptionServerURL(new URL("https://example.com/push")),
      types = Seq(CustomTypeName1))
    val pushSubscriptionId = SMono.fromPublisher(testee.save(ALICE, validRequest)).block().id
    SMono.fromPublisher(testee.updateExpireTime(ALICE, pushSubscriptionId, MAX_EXPIRE.plusDays(1))).block()

    val updatedSubscription = SFlux.fromPublisher(testee.get(ALICE, Set(pushSubscriptionId).asJava)).blockFirst().get
    assertThat(updatedSubscription.expires.value).isEqualTo(MAX_EXPIRE)
  }

  @Test
  def updateExpiresWithNotFoundPushSubscriptionIdShouldThrowException(): Unit = {
    val randomId = PushSubscriptionId.generate()

    assertThatThrownBy(() => SMono.fromPublisher(testee.updateExpireTime(ALICE, randomId, VALID_EXPIRE)).block())
      .isInstanceOf(classOf[PushSubscriptionNotFoundException])
  }

  @Test
  def updateWithValidExpiresShouldSucceed(): Unit = {
    val validRequest = PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      url = PushSubscriptionServerURL(new URL("https://example.com/push")),
      types = Seq(CustomTypeName1))
    val pushSubscriptionId = SMono.fromPublisher(testee.save(ALICE, validRequest)).block().id
    SMono.fromPublisher(testee.updateExpireTime(ALICE, pushSubscriptionId, VALID_EXPIRE)).block()

    val updatedSubscription = SFlux.fromPublisher(testee.get(ALICE, Set(pushSubscriptionId).asJava)).blockFirst().get
    assertThat(updatedSubscription.expires.value).isEqualTo(VALID_EXPIRE)
  }

  @Test
  def updateWithExpiresBiggerThanMaxExpiresShouldReturnServerFixedExpires(): Unit = {
    val validRequest = PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      url = PushSubscriptionServerURL(new URL("https://example.com/push")),
      types = Seq(CustomTypeName1))
    val pushSubscriptionId = SMono.fromPublisher(testee.save(ALICE, validRequest)).block().id
    val fixedExpires = SMono.fromPublisher(testee.updateExpireTime(ALICE, pushSubscriptionId, MAX_EXPIRE.plusDays(1))).block()

    assertThat(fixedExpires).isEqualTo(PushSubscriptionExpiredTime(MAX_EXPIRE))
  }

  @Test
  def updateWithValidTypesShouldSucceed(): Unit = {
    val validRequest = PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      url = PushSubscriptionServerURL(new URL("https://example.com/push")),
      types = Seq(CustomTypeName1))
    val pushSubscriptionId = SMono.fromPublisher(testee.save(ALICE, validRequest)).block().id

    val newTypes: Set[TypeName] = Set(CustomTypeName1, CustomTypeName2)
    SMono.fromPublisher(testee.updateTypes(ALICE, pushSubscriptionId, newTypes.asJava)).block()

    val updatedSubscription = SFlux.fromPublisher(testee.get(ALICE, Set(pushSubscriptionId).asJava)).blockFirst().get
    assertThat(updatedSubscription.types.toSet.asJava).containsExactlyInAnyOrder(CustomTypeName1, CustomTypeName2)
  }

  @Test
  def updateTypesWithNotFoundShouldThrowException(): Unit = {
    val randomId = PushSubscriptionId.generate()
    val newTypes: Set[TypeName] = Set(CustomTypeName1, CustomTypeName2)

    assertThatThrownBy(() => SMono.fromPublisher(testee.updateTypes(ALICE, randomId, newTypes.asJava)).block())
      .isInstanceOf(classOf[PushSubscriptionNotFoundException])
  }

  @Test
  def getNotFoundShouldReturnEmpty(): Unit = {
    val randomId = PushSubscriptionId.generate()

    assertThat(SMono.fromPublisher(testee.get(ALICE, Set(randomId).asJava)).blockOption().toJava)
      .isEmpty
  }

  @Test
  def revokeStoredSubscriptionShouldSucceed(): Unit = {
    val validRequest = PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      url = PushSubscriptionServerURL(new URL("https://example.com/push")),
      types = Seq(CustomTypeName1))
    val pushSubscriptionId = SMono.fromPublisher(testee.save(ALICE, validRequest)).block().id
    val singleRecordSaved = SFlux.fromPublisher(testee.get(ALICE, Set(pushSubscriptionId).asJava)).count().block()
    assertThat(singleRecordSaved).isEqualTo(1)

    SMono.fromPublisher(testee.revoke(ALICE, pushSubscriptionId)).block()
    val remaining = SFlux.fromPublisher(testee.get(ALICE, Set(pushSubscriptionId).asJava)).collectSeq().block().asJava

    assertThat(remaining).isEmpty()
  }

  @Test
  def deleteStoredSubscriptionShouldSucceed(): Unit = {
    val validRequest = PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      url = PushSubscriptionServerURL(new URL("https://example.com/push")),
      types = Seq(CustomTypeName1))
    val pushSubscriptionId = SMono.fromPublisher(testee.save(ALICE, validRequest)).block().id
    val singleRecordSaved = SFlux.fromPublisher(testee.get(ALICE, Set(pushSubscriptionId).asJava)).count().block()
    assertThat(singleRecordSaved).isEqualTo(1)

    SMono.fromPublisher(testee.delete(ALICE)).block()
    val remaining = SFlux.fromPublisher(testee.get(ALICE, Set(pushSubscriptionId).asJava)).collectSeq().block().asJava

    assertThat(remaining).isEmpty()
  }

  @Test
  def revokeNotFoundShouldNotFail(): Unit = {
    val pushSubscriptionId = PushSubscriptionId.generate()
    assertThatCode(() => SMono.fromPublisher(testee.revoke(ALICE, pushSubscriptionId)).block())
      .doesNotThrowAnyException()
  }

  @Test
  def deleteNotFoundShouldNotFail(): Unit = {
    val pushSubscriptionId = PushSubscriptionId.generate()
    assertThatCode(() => SMono.fromPublisher(testee.delete(ALICE)).block())
      .doesNotThrowAnyException()
  }

  @Test
  def getStoredSubscriptionShouldSucceed(): Unit = {
    val deviceClientId1 = DeviceClientId("1")
    val deviceClientId2 = DeviceClientId("2")
    val validRequest1 = PushSubscriptionCreationRequest(
      deviceClientId = deviceClientId1,
      url = PushSubscriptionServerURL(new URL("https://example.com/push")),
      expires = Option(PushSubscriptionExpiredTime(VALID_EXPIRE)),
      types = Seq(CustomTypeName1))
    val validRequest2 = PushSubscriptionCreationRequest(
      deviceClientId = deviceClientId2,
      url = PushSubscriptionServerURL(new URL("https://example.com/push")),
      expires = Option(PushSubscriptionExpiredTime(VALID_EXPIRE)),
      types = Seq(CustomTypeName2))
    val pushSubscriptionId1 = SMono.fromPublisher(testee.save(ALICE, validRequest1)).block().id
    val pushSubscriptionId2 = SMono.fromPublisher(testee.save(ALICE, validRequest2)).block().id

    val pushSubscriptions = SFlux.fromPublisher(testee.get(ALICE, Set(pushSubscriptionId1, pushSubscriptionId2).asJava)).collectSeq().block()

    assertThat(pushSubscriptions.map(_.id).toList.asJava).containsExactlyInAnyOrder(pushSubscriptionId1, pushSubscriptionId2)
  }

  @Test
  def getShouldMixFoundAndNotFound(): Unit = {
    val deviceClientId1 = DeviceClientId("1")
    val validRequest1 = PushSubscriptionCreationRequest(
      deviceClientId = deviceClientId1,
      url = PushSubscriptionServerURL(new URL("https://example.com/push")),
      expires = Option(PushSubscriptionExpiredTime(VALID_EXPIRE)),
      types = Seq(CustomTypeName1))
    val pushSubscriptionId1 = SMono.fromPublisher(testee.save(ALICE, validRequest1)).block().id
    val pushSubscriptionId2 = PushSubscriptionId.generate()

    val pushSubscriptions = SFlux.fromPublisher(testee.get(ALICE, Set(pushSubscriptionId1, pushSubscriptionId2).asJava)).collectSeq().block()

    assertThat(pushSubscriptions.map(_.id).toList.asJava).containsExactlyInAnyOrder(pushSubscriptionId1)
  }

  @Test
  def getSubscriptionShouldReturnExpiredSubscriptions(): Unit = {
    val validRequest1 = PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      url = PushSubscriptionServerURL(new URL("https://example.com/push")),
      expires = Option(PushSubscriptionExpiredTime(VALID_EXPIRE.plusDays(1))),
      types = Seq(CustomTypeName1))
    val pushSubscriptionId = SMono.fromPublisher(testee.save(ALICE, validRequest1)).block().id

    clock.setInstant(VALID_EXPIRE.plusDays(2).toInstant)

    val pushSubscriptions = SFlux.fromPublisher(testee.get(ALICE, Set(pushSubscriptionId).asJava)).collectSeq().block()

    assertThat(pushSubscriptions.map(_.id).toList.asJava).containsOnly(pushSubscriptionId)
  }

  @Test
  def listStoredSubscriptionShouldSucceed(): Unit = {
    val deviceClientId1 = DeviceClientId("1")
    val deviceClientId2 = DeviceClientId("2")
    val validRequest1 = PushSubscriptionCreationRequest(
      deviceClientId = deviceClientId1,
      url = PushSubscriptionServerURL(new URL("https://example.com/push")),
      types = Seq(CustomTypeName1))
    val validRequest2 = PushSubscriptionCreationRequest(
      deviceClientId = deviceClientId2,
      url = PushSubscriptionServerURL(new URL("https://example.com/push")),
      types = Seq(CustomTypeName2))
    val pushSubscriptionId1: PushSubscriptionId = SMono.fromPublisher(testee.save(ALICE, validRequest1)).block().id
    val pushSubscriptionId2: PushSubscriptionId = SMono.fromPublisher(testee.save(ALICE, validRequest2)).block().id

    val idList: List[PushSubscription] = SFlux(testee.list(ALICE)).collectSeq().block().toList

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(idList.map(_.id).asJava).containsExactlyInAnyOrder(pushSubscriptionId1, pushSubscriptionId2)
      softly.assertThat(idList.map(_.deviceClientId).asJava).containsExactlyInAnyOrder(deviceClientId1, deviceClientId2)
    })
  }

  @Test
  def listSubscriptionShouldReturnExpiredSubscriptions(): Unit = {
    val validRequest1 = PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      url = PushSubscriptionServerURL(new URL("https://example.com/push")),
      expires = Option(PushSubscriptionExpiredTime(VALID_EXPIRE.plusDays(1))),
      types = Seq(CustomTypeName1))
    val pushSubscriptionId = SMono.fromPublisher(testee.save(ALICE, validRequest1)).block().id

    clock.setInstant(VALID_EXPIRE.plusDays(2).toInstant)

    val pushSubscriptions = SFlux.fromPublisher(testee.list(ALICE)).collectSeq().block()

    assertThat(pushSubscriptions.map(_.id).toList.asJava).containsOnly(pushSubscriptionId)
  }

  @Test
  def validateVerificationCodeShouldSucceed(): Unit = {
    val validRequest = PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      url = PushSubscriptionServerURL(new URL("https://example.com/push")),
      types = Seq(CustomTypeName1))
    val pushSubscriptionId = SMono.fromPublisher(testee.save(ALICE, validRequest)).block().id
    SMono.fromPublisher(testee.validateVerificationCode(ALICE, pushSubscriptionId)).block()

    val validatedSubscription = SFlux.fromPublisher(testee.get(ALICE, Set(pushSubscriptionId).asJava)).blockFirst().get
    assertThat(validatedSubscription.validated).isEqualTo(true)
  }

  @Test
  def validateVerificationCodeWithNotFoundPushSubscriptionIdShouldThrowException(): Unit = {
    val randomId = PushSubscriptionId.generate()

    assertThatThrownBy(() => SMono.fromPublisher(testee.validateVerificationCode(ALICE, randomId)).block())
      .isInstanceOf(classOf[PushSubscriptionNotFoundException])
  }

  @Test
  def saveSubscriptionWithFullKeyPairShouldSucceed(): Unit = {
    val fullKeyPair = Some(PushSubscriptionKeys(p256dh = "p256h", auth = "auth"))
    val validRequest = PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      url = PushSubscriptionServerURL(new URL("https://example.com/push")),
      types = Seq(CustomTypeName1),
      keys = fullKeyPair)

    val pushSubscriptionId1 = SMono.fromPublisher(testee.save(ALICE, validRequest)).block().id

    val pushSubscriptions = SFlux.fromPublisher(testee.get(ALICE, Set(pushSubscriptionId1).asJava)).collectSeq().block()

    assertThat(pushSubscriptions.map(_.keys).toList.asJava).containsExactlyInAnyOrder(fullKeyPair)
  }

  @Test
  def saveSubscriptionWithNoneKeyPairShouldSucceed(): Unit = {
    val emptyKeyPair = None
    val validRequest = PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      url = PushSubscriptionServerURL(new URL("https://example.com/push")),
      types = Seq(CustomTypeName1),
      keys = emptyKeyPair)
    val pushSubscriptionId1 = SMono.fromPublisher(testee.save(ALICE, validRequest)).block().id

    val pushSubscriptions = SFlux.fromPublisher(testee.get(ALICE, Set(pushSubscriptionId1).asJava)).collectSeq().block()

    assertThat(pushSubscriptions.map(_.keys).toList.asJava).containsExactlyInAnyOrder(emptyKeyPair)
  }

  @Test
  def saveSubscriptionWithEmptyP256hKeyShouldFail(): Unit = {
    val emptyP256hKey = Some(PushSubscriptionKeys.apply(p256dh = "", auth = "auth"))

    val validRequest = PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      url = PushSubscriptionServerURL(new URL("https://example.com/push")),
      types = Seq(CustomTypeName1),
      keys = emptyP256hKey)

    assertThatThrownBy(() => SMono.fromPublisher(testee.save(ALICE, validRequest)).block())
      .isInstanceOf(classOf[InvalidPushSubscriptionKeys])
  }

  @Test
  def saveSubscriptionWithEmptyAuthKeyShouldFail(): Unit = {
    val emptyAuthKey = Some(PushSubscriptionKeys.apply(p256dh = "p256dh", auth = ""))

    val validRequest = PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      url = PushSubscriptionServerURL(new URL("https://example.com/push")),
      types = Seq(CustomTypeName1),
      keys = emptyAuthKey)

    assertThatThrownBy(() => SMono.fromPublisher(testee.save(ALICE, validRequest)).block())
      .isInstanceOf(classOf[InvalidPushSubscriptionKeys])
  }

  @Test
  def updateShouldUpdateCorrectOffsetDateTime(): Unit = {
    val validRequest = PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("1"),
      url = PushSubscriptionServerURL(new URL("https://example.com/push")),
      types = Seq(CustomTypeName1))

    val pushSubscriptionId = SMono.fromPublisher(testee.save(ALICE, validRequest)).block().id

    val ZONE_ID: ZoneId = ZoneId.of("Europe/Paris")
    val CLOCK: Clock = Clock.fixed(Instant.parse("2021-10-25T07:05:39.160Z"), ZONE_ID)

    val zonedDateTime: ZonedDateTime = ZonedDateTime.now(CLOCK)
    SMono.fromPublisher(testee.updateExpireTime(ALICE, pushSubscriptionId, zonedDateTime)).block()

    val updatedSubscription = SFlux.fromPublisher(testee.get(ALICE, Set(pushSubscriptionId).asJava)).blockFirst().get
    assertThat(updatedSubscription.expires.value).isEqualTo(zonedDateTime)
  }

}

