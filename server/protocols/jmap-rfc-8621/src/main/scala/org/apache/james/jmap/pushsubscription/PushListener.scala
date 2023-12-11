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
import java.time.Clock
import java.util.Base64

import com.google.common.annotations.VisibleForTesting
import com.google.common.hash.Hashing
import javax.inject.Inject
import org.apache.james.events.EventListener.ReactiveGroupEventListener
import org.apache.james.events.{Event, Group}
import org.apache.james.jmap.api.model.PushSubscription
import org.apache.james.jmap.api.pushsubscription.PushSubscriptionHelpers.isNotOutdatedSubscription
import org.apache.james.jmap.api.pushsubscription.PushSubscriptionRepository
import org.apache.james.jmap.change.{EmailDeliveryTypeName, StateChangeEvent}
import org.apache.james.jmap.core.StateChange
import org.apache.james.jmap.json.PushSerializer
import org.apache.james.jmap.pushsubscription.PushListener.extractTopic
import org.apache.james.jmap.pushsubscription.PushTopic.PushTopic
import org.apache.james.user.api.DelegationStore
import org.apache.james.util.ReactorUtils
import org.reactivestreams.Publisher
import play.api.libs.json.Json
import reactor.core.scala.publisher.SMono

case class PushListenerGroup() extends Group {}

object PushListener {
  @VisibleForTesting
  def extractTopic(stateChange: StateChange): PushTopic =
    PushTopic.validate(
      Base64.getUrlEncoder()
        .encodeToString(
          Hashing.murmur3_128()
            .hashString(stateChange.changes
              .toList
              .map {
                case (accountId, typeState) => accountId.id.value + "@" + typeState.changes.keys.hashCode()
              }.mkString("&"), StandardCharsets.UTF_8)
            .asBytes()))
      .toOption.get
}

class PushListener @Inject()(pushRepository: PushSubscriptionRepository,
                             webPushClient: WebPushClient,
                             pushSerializer: PushSerializer,
                             delegationStore: DelegationStore,
                             clock: Clock) extends ReactiveGroupEventListener {

  override def getDefaultGroup: Group = PushListenerGroup()

  override def reactiveEvent(event: Event): Publisher[Void] =
    event match {
      case event: StateChangeEvent =>
        SMono.just(event.username)
          .concatWith(delegationStore.authorizedUsers(event.username))
          .flatMap(pushRepository.list)
          .filter(isNotOutdatedSubscription(_, clock))
          .filter(_.validated)
          .flatMap(sendNotification(_, event), ReactorUtils.DEFAULT_CONCURRENCY)
          .`then`()
      case _ => SMono.empty
    }

  override def isHandling(event: Event): Boolean = event.isInstanceOf[StateChangeEvent]

  private def sendNotification(pushSubscription: PushSubscription, stateChangeEvent: StateChangeEvent): Publisher[Unit] =
    stateChangeEvent
      .asStateChange
      .filter(pushSubscription.types.toSet)
      .fold(SMono.empty[Unit])(stateChange => SMono(webPushClient.push(pushSubscription.url, asPushRequest(stateChange, pushSubscription))))

  private def asPushRequest(stateChange: StateChange, pushSubscription: PushSubscription): PushRequest =
    PushRequest(ttl = PushTTL.MAX,
      urgency = Some(urgency(stateChange)),
      topic = Some(extractTopic(stateChange)),
      contentCoding = pushSubscription.keys.map(_ => Aes128gcm),
      payload = asBytes(stateChange, pushSubscription))

  private def asBytes(stateChange: StateChange, pushSubscription: PushSubscription) = {
    val clearTextPayload = Json.stringify(pushSerializer.serializeSSE(stateChange)).getBytes(StandardCharsets.UTF_8)
    pushSubscription.keys
      .map(keys => keys.encrypt(clearTextPayload))
      .getOrElse(clearTextPayload)
  }

  private def urgency(stateChange: StateChange): PushUrgency =
    if (stateChange.changes
      .values
      .flatMap(_.changes.keys)
      .toList
      .contains(EmailDeliveryTypeName)) {
      High
    } else {
      Low
    }
}
