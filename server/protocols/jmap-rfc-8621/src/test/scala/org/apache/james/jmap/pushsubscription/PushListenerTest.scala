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

package org.apache.james.jmap.pushsubscription

import java.nio.charset.StandardCharsets
import java.security.interfaces.{ECPrivateKey, ECPublicKey}
import java.time.Clock
import java.util.{Base64, UUID}

import com.google.common.collect.ImmutableSet
import com.google.common.hash.Hashing
import com.google.crypto.tink.apps.webpush.WebPushHybridDecrypt
import com.google.crypto.tink.subtle.EllipticCurves.CurveType
import com.google.crypto.tink.subtle.{EllipticCurves, Random}
import org.apache.james.core.Username
import org.apache.james.events.Event.EventId
import org.apache.james.jmap.api.change.TypeStateFactory
import org.apache.james.jmap.api.model.{DeviceClientId, PushSubscriptionCreationRequest, PushSubscriptionKeys, PushSubscriptionServerURL, TypeName}
import org.apache.james.jmap.api.pushsubscription.PushSubscriptionRepository
import org.apache.james.jmap.change.{EmailDeliveryTypeName, EmailTypeName, MailboxTypeName, StateChangeEvent, TypeState}
import org.apache.james.jmap.core.{AccountId, PushState, StateChange, UuidState}
import org.apache.james.jmap.json.PushSerializer
import org.apache.james.jmap.memory.pushsubscription.MemoryPushSubscriptionRepository
import org.apache.james.user.api.DelegationStore
import org.apache.james.user.memory.MemoryDelegationStore
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.{BeforeEach, Nested, Test}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, times, verify, verifyNoInteractions, when}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import reactor.core.scala.publisher.SMono

import scala.jdk.OptionConverters._

class PushListenerTest {
  val bob: Username = Username.of("bob@localhost")
  val alice: Username = Username.of("alice@localhost")
  val bobAccountId: String = "405010d6c16c16dec36f3d7a7596c2d757ba7b57904adc4801a63e40914fd5c9"
  val aliceAccountId: String = "93c56f4408cff66f0a929aea8e3940e753c3275e5622582ae3010e7277b7696c"
  val url: PushSubscriptionServerURL = PushSubscriptionServerURL.from("http://localhost:9999/push").get

  var testee: PushListener = _
  var pushSubscriptionRepository: PushSubscriptionRepository = _
  var webPushClient: WebPushClient = _
  var delegationStore: DelegationStore = _

  @BeforeEach
  def setUp(): Unit = {
    val pushSerializer = PushSerializer(TypeStateFactory(ImmutableSet.of[TypeName](MailboxTypeName, EmailTypeName, EmailDeliveryTypeName)))

    pushSubscriptionRepository = new MemoryPushSubscriptionRepository(Clock.systemUTC())
    webPushClient = mock(classOf[WebPushClient])
    delegationStore = new MemoryDelegationStore()
    testee = new PushListener(pushSubscriptionRepository, webPushClient, pushSerializer, delegationStore, Clock.systemUTC())

    when(webPushClient.push(any(), any())).thenReturn(SMono.empty[Unit])
  }

  @Test
  def shouldNotPushWhenNoSubscriptions(): Unit = {
    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), bob,
      Map(EmailTypeName -> UuidState(UUID.randomUUID()))))).block()

    verifyNoInteractions(webPushClient)
  }

  @Test
  def shouldNotPushWhenNotVerified(): Unit = {
    SMono(pushSubscriptionRepository.save(bob, PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit"),
      url = url,
      types = Seq(MailboxTypeName, EmailTypeName)))).block()

    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), bob,
      Map(EmailTypeName -> UuidState(UUID.randomUUID()))))).block()

    verifyNoInteractions(webPushClient)
  }

  @Test
  def shouldNotPushWhenTypeMismatch(): Unit = {
    val id = SMono(pushSubscriptionRepository.save(bob, PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit"),
      url = url,
      types = Seq(EmailDeliveryTypeName)))).block().id
    SMono(pushSubscriptionRepository.validateVerificationCode(bob, id)).block()

    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), bob,
      Map(EmailTypeName -> UuidState(UUID.randomUUID()))))).block()

    verifyNoInteractions(webPushClient)
  }

  @Test
  def shouldPushWhenValidated(): Unit = {
    val id = SMono(pushSubscriptionRepository.save(bob, PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit"),
      url = url,
      types = Seq(EmailTypeName, MailboxTypeName)))).block().id
    SMono(pushSubscriptionRepository.validateVerificationCode(bob, id)).block()

    val state1 = UuidState(UUID.randomUUID())
    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), bob,
      Map(EmailTypeName -> state1)))).block()

    val argumentCaptor: ArgumentCaptor[PushRequest] = ArgumentCaptor.forClass(classOf[PushRequest])
    verify(webPushClient).push(ArgumentMatchers.eq(url), argumentCaptor.capture())

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(argumentCaptor.getValue.ttl).isEqualTo(PushTTL.MAX)
      softly.assertThat(new String(argumentCaptor.getValue.payload, StandardCharsets.UTF_8))
        .isEqualTo(s"""{"@type":"StateChange","changed":{"$bobAccountId":{"Email":"${state1.value.toString}"}}}""")
    })
  }

  @Test
  def shouldNotPushAliceChangesToBobWhenBobIsNotDelegatedByAlice(): Unit = {
    val bobSubscriptionId = SMono(pushSubscriptionRepository.save(bob, PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit1"),
      url = url,
      types = Seq(EmailTypeName, MailboxTypeName)))).block().id
    SMono(pushSubscriptionRepository.validateVerificationCode(bob, bobSubscriptionId)).block()

    val state1 = UuidState(UUID.randomUUID())
    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), alice, Map(EmailTypeName -> state1)))).block()

    verify(webPushClient, times(0)).push(ArgumentMatchers.eq(url), any())
  }

  @Test
  def shouldPushAliceChangesToAliceAndBobWhenBobIsDelegatedByAlice(): Unit = {
    SMono.fromPublisher(delegationStore.addAuthorizedUser(alice, bob)).block()

    val bobSubscriptionId = SMono(pushSubscriptionRepository.save(bob, PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit1"),
      url = url,
      types = Seq(EmailTypeName, MailboxTypeName)))).block().id
    SMono(pushSubscriptionRepository.validateVerificationCode(bob, bobSubscriptionId)).block()
    val aliceSubscriptionId = SMono(pushSubscriptionRepository.save(alice, PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit2"),
      url = url,
      types = Seq(EmailTypeName, MailboxTypeName)))).block().id
    SMono(pushSubscriptionRepository.validateVerificationCode(alice, aliceSubscriptionId)).block()

    val state1 = UuidState(UUID.randomUUID())
    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), alice, Map(EmailTypeName -> state1)))).block()

    val argumentCaptor: ArgumentCaptor[PushRequest] = ArgumentCaptor.forClass(classOf[PushRequest])
    verify(webPushClient, times(2)).push(ArgumentMatchers.eq(url), argumentCaptor.capture())
    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(new String(argumentCaptor.getAllValues.get(0).payload, StandardCharsets.UTF_8))
        .isEqualTo(s"""{"@type":"StateChange","changed":{"$aliceAccountId":{"Email":"${state1.value.toString}"}}}""")
      softly.assertThat(new String(argumentCaptor.getAllValues.get(1).payload, StandardCharsets.UTF_8))
        .isEqualTo(s"""{"@type":"StateChange","changed":{"$aliceAccountId":{"Email":"${state1.value.toString}"}}}""")
    })
  }

  @Test
  def bobShouldReceiveHisChangesAndAliceChangesWhenBobIsDelegatedByAlice(): Unit = {
    SMono.fromPublisher(delegationStore.addAuthorizedUser(alice, bob)).block()

    val bobSubscriptionId = SMono(pushSubscriptionRepository.save(bob, PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit1"),
      url = url,
      types = Seq(EmailTypeName, MailboxTypeName)))).block().id
    SMono(pushSubscriptionRepository.validateVerificationCode(bob, bobSubscriptionId)).block()

    val stateChangeBob = UuidState(UUID.randomUUID())
    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), bob, Map(EmailTypeName -> stateChangeBob)))).block()
    val stateChangeAlice = UuidState(UUID.randomUUID())
    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), alice, Map(EmailTypeName -> stateChangeAlice)))).block()

    val argumentCaptor: ArgumentCaptor[PushRequest] = ArgumentCaptor.forClass(classOf[PushRequest])
    verify(webPushClient, times(2)).push(ArgumentMatchers.eq(url), argumentCaptor.capture())
    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(new String(argumentCaptor.getAllValues.get(0).payload, StandardCharsets.UTF_8))
        .isEqualTo(s"""{"@type":"StateChange","changed":{"$bobAccountId":{"Email":"${stateChangeBob.value.toString}"}}}""")
      softly.assertThat(new String(argumentCaptor.getAllValues.get(1).payload, StandardCharsets.UTF_8))
        .isEqualTo(s"""{"@type":"StateChange","changed":{"$aliceAccountId":{"Email":"${stateChangeAlice.value.toString}"}}}""")
    })
  }

  @Test
  def unwantedTypesShouldBeFilteredOut(): Unit = {
    val id = SMono(pushSubscriptionRepository.save(bob, PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit"),
      url = url,
      types = Seq(EmailTypeName, MailboxTypeName)))).block().id
    SMono(pushSubscriptionRepository.validateVerificationCode(bob, id)).block()

    val state1 = UuidState(UUID.randomUUID())
    val state2 = UuidState(UUID.randomUUID())
    val state3 = UuidState(UUID.randomUUID())
    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), bob,
      Map(EmailTypeName -> state1, MailboxTypeName -> state2, EmailDeliveryTypeName -> state3)))).block()

    val argumentCaptor: ArgumentCaptor[PushRequest] = ArgumentCaptor.forClass(classOf[PushRequest])
    verify(webPushClient).push(ArgumentMatchers.eq(url), argumentCaptor.capture())

    SoftAssertions.assertSoftly(softly => {
      softly.assertThat(argumentCaptor.getValue.ttl).isEqualTo(PushTTL.MAX)
      softly.assertThat(new String(argumentCaptor.getValue.payload, StandardCharsets.UTF_8))
        .isEqualTo(s"""{"@type":"StateChange","changed":{"$bobAccountId":{"Email":"${state1.value.toString}","Mailbox":"${state2.value.toString}"}}}""")
    })
  }

  @Test
  def emailDeliveryUrgencyShouldBeHigh(): Unit = {
    val id = SMono(pushSubscriptionRepository.save(bob, PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit"),
      url = url,
      types = Seq(EmailDeliveryTypeName, EmailTypeName)))).block().id
    SMono(pushSubscriptionRepository.validateVerificationCode(bob, id)).block()

    val state1 = UuidState(UUID.randomUUID())
    val state2 = UuidState(UUID.randomUUID())
    val state3 = UuidState(UUID.randomUUID())
    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), bob,
      Map(EmailTypeName -> state1, MailboxTypeName -> state2, EmailDeliveryTypeName -> state3)))).block()

    val argumentCaptor: ArgumentCaptor[PushRequest] = ArgumentCaptor.forClass(classOf[PushRequest])
    verify(webPushClient).push(ArgumentMatchers.eq(url), argumentCaptor.capture())
    assertThat(argumentCaptor.getValue.urgency.toJava).contains(High)
  }

  @Test
  def nonEmailDeliveryUrgencyShouldBeLow(): Unit = {
    val id = SMono(pushSubscriptionRepository.save(bob, PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit"),
      url = url,
      types = Seq(EmailDeliveryTypeName, EmailTypeName)))).block().id
    SMono(pushSubscriptionRepository.validateVerificationCode(bob, id)).block()

    val state1 = UuidState(UUID.randomUUID())
    val state2 = UuidState(UUID.randomUUID())
    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), bob,
      Map(EmailTypeName -> state1, MailboxTypeName -> state2)))).block()

    val argumentCaptor: ArgumentCaptor[PushRequest] = ArgumentCaptor.forClass(classOf[PushRequest])
    verify(webPushClient).push(ArgumentMatchers.eq(url), argumentCaptor.capture())
    assertThat(argumentCaptor.getValue.urgency.toJava).contains(Low)
  }

  @Test
  def topicShouldBeSpecified(): Unit = {
    val id = SMono(pushSubscriptionRepository.save(bob, PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit"),
      url = url,
      types = Seq(EmailDeliveryTypeName, EmailTypeName)))).block().id
    SMono(pushSubscriptionRepository.validateVerificationCode(bob, id)).block()

    val state1 = UuidState(UUID.randomUUID())
    val state2 = UuidState(UUID.randomUUID())
    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), bob,
      Map(EmailTypeName -> state1, MailboxTypeName -> state2)))).block()

    val argumentCaptor: ArgumentCaptor[PushRequest] = ArgumentCaptor.forClass(classOf[PushRequest])
    verify(webPushClient).push(ArgumentMatchers.eq(url), argumentCaptor.capture())
    assertThat(argumentCaptor.getValue.topic.toJava)
      .contains(PushTopic.validate("j2HKoW6BCasQ1URmX6SRjg==").toOption.get)
  }

  @Test
  def pushShouldEncryptMessages(): Unit = {
    val uaKeyPair = EllipticCurves.generateKeyPair(CurveType.NIST_P256)
    val uaPrivateKey: ECPrivateKey = uaKeyPair.getPrivate.asInstanceOf[ECPrivateKey]
    val uaPublicKey: ECPublicKey = uaKeyPair.getPublic.asInstanceOf[ECPublicKey]
    val authSecret = Random.randBytes(16)

    val hybridDecrypt = new WebPushHybridDecrypt.Builder()
      .withAuthSecret(authSecret)
      .withRecipientPublicKey(uaPublicKey)
      .withRecipientPrivateKey(uaPrivateKey)
      .build

    val id = SMono(pushSubscriptionRepository.save(bob, PushSubscriptionCreationRequest(
      deviceClientId = DeviceClientId("junit"),
      keys = Some(PushSubscriptionKeys(p256dh = Base64.getUrlEncoder.encodeToString(uaPublicKey.getEncoded),
        auth = Base64.getUrlEncoder.encodeToString(authSecret))),
      url = url,
      types = Seq(EmailTypeName, MailboxTypeName)))).block().id
    SMono(pushSubscriptionRepository.validateVerificationCode(bob, id)).block()

    val state1 = UuidState(UUID.randomUUID())
    val state2 = UuidState(UUID.randomUUID())
    val state3 = UuidState(UUID.randomUUID())
    SMono(testee.reactiveEvent(StateChangeEvent(EventId.random(), bob,
      Map(EmailTypeName -> state1, MailboxTypeName -> state2, EmailDeliveryTypeName -> state3)))).block()

    val argumentCaptor: ArgumentCaptor[PushRequest] = ArgumentCaptor.forClass(classOf[PushRequest])
    verify(webPushClient).push(ArgumentMatchers.eq(url), argumentCaptor.capture())
    val decryptedPayload = s"""{"@type":"StateChange","changed":{"$bobAccountId":{"Email":"${state1.value.toString}","Mailbox":"${state2.value.toString}"}}}"""
    val encryptedPayload = argumentCaptor.getValue.payload
    SoftAssertions.assertSoftly(softly => {
      // We positionned well the Content-Encoding header
      softly.assertThat(argumentCaptor.getValue.contentCoding.toJava).contains(Aes128gcm)
      // We are able to decrypt the payload
      softly.assertThat(new String(hybridDecrypt.decrypt(encryptedPayload, null), StandardCharsets.UTF_8))
        .isEqualTo(decryptedPayload)
      // The content had been well modified by the encryption
      softly.assertThat(Hashing.sha256().hashBytes(encryptedPayload))
        .isNotEqualTo(Hashing.sha256().hashBytes(decryptedPayload.getBytes(StandardCharsets.UTF_8)))
    })
  }

  @Nested
  class ExtractPushTopic {
    private val bobAccount: AccountId = AccountId.from(bob).toOption.get
    private val aliceAccount: AccountId = AccountId.from(alice).toOption.get

    @Test
    def extractTopicShouldBeIndependentFromState(): Unit = {
      val state1 = UuidState(UUID.randomUUID())
      assertThat(PushListener.extractTopic(StateChange(Map(bobAccount -> TypeState(Map(EmailTypeName -> state1))), None)))
        .isEqualTo(PushTopic.validate("j2HKoW6BCasQ1URmX6SRjg==").toOption.get)
    }

    @Test
    def extractTopicShouldDependOnTypeName(): Unit = {
      val state1 = UuidState(UUID.randomUUID())
      assertThat(PushListener.extractTopic(StateChange(Map(bobAccount -> TypeState(Map(EmailDeliveryTypeName -> state1))), None)))
        .isNotEqualTo(PushListener.extractTopic(StateChange(Map(bobAccount -> TypeState(Map(EmailTypeName -> state1))), None)))
        .isEqualTo(PushTopic.validate("jCtTpf_BO3ZG03loJXHRPQ==").toOption.get)
    }

    @Test
    def extractTopicShouldDependOnExtraTypeName(): Unit = {
      val state1 = UuidState(UUID.randomUUID())
      assertThat(PushListener.extractTopic(StateChange(Map(bobAccount -> TypeState(Map(EmailTypeName -> state1, EmailDeliveryTypeName -> state1))), None)))
        .isNotEqualTo(PushListener.extractTopic(StateChange(Map(bobAccount -> TypeState(Map(EmailTypeName -> state1))), None)))
        .isEqualTo(PushTopic.validate("oA4xCCqJhc98SKLJOa-ndA==").toOption.get)
    }

    @Test
    def extractTopicShouldNotDependOnExtraTypeNameOrdering(): Unit = {
      val state1 = UuidState(UUID.randomUUID())

      assertThat(PushListener.extractTopic(StateChange(Map(bobAccount -> TypeState(Map(EmailDeliveryTypeName -> state1, EmailTypeName -> state1))), None)))
        .isEqualTo(PushListener.extractTopic(StateChange(Map(bobAccount -> TypeState(Map(EmailTypeName -> state1, EmailDeliveryTypeName -> state1))), None)))
        .isEqualTo(PushTopic.validate("oA4xCCqJhc98SKLJOa-ndA==").toOption.get)
    }

    @Test
    def extractTopicShouldNotDependOnPushState(): Unit = {
      val state1 = UuidState(UUID.randomUUID())

      assertThat(PushListener.extractTopic(StateChange(Map(bobAccount -> TypeState(Map(EmailDeliveryTypeName -> state1, EmailTypeName -> state1))), Some(PushState("whatever")))))
        .isEqualTo(PushListener.extractTopic(StateChange(Map(bobAccount -> TypeState(Map(EmailTypeName -> state1, EmailDeliveryTypeName -> state1))), None)))
        .isEqualTo(PushTopic.validate("oA4xCCqJhc98SKLJOa-ndA==").toOption.get)
    }

    @Test
    def extractTopicShouldDependOnAccountId(): Unit = {
      val state1 = UuidState(UUID.randomUUID())
      assertThat(PushListener.extractTopic(StateChange(Map(aliceAccount -> TypeState(Map(EmailTypeName -> state1))), None)))
        .isNotEqualTo(PushListener.extractTopic(StateChange(Map(bobAccount -> TypeState(Map(EmailTypeName -> state1))), None)))
        .isEqualTo(PushTopic.validate("PQSgEID7KHXJ6AbqipDETQ==").toOption.get)
    }

    @Test
    def extractTopicShouldDependOnExtraAccountId(): Unit = {
      val state1 = UuidState(UUID.randomUUID())
      assertThat(PushListener.extractTopic(StateChange(Map(
          aliceAccount -> TypeState(Map(EmailTypeName -> state1)),
          bobAccount -> TypeState(Map(EmailTypeName -> state1))),
        None)))
        .isNotEqualTo(PushListener.extractTopic(StateChange(Map(bobAccount -> TypeState(Map(EmailTypeName -> state1))), None)))
        .isEqualTo(PushTopic.validate("C4Q8cdY9ja_dkMGE7xZomQ==").toOption.get)
    }
  }
}
