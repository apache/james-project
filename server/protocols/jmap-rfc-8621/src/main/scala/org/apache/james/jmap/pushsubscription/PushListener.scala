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

import javax.inject.Inject
import org.apache.james.events.EventListener.ReactiveGroupEventListener
import org.apache.james.events.{Event, Group}
import org.apache.james.jmap.api.model.PushSubscription
import org.apache.james.jmap.api.pushsubscription.PushSubscriptionRepository
import org.apache.james.jmap.change.StateChangeEvent
import org.apache.james.jmap.core.StateChange
import org.apache.james.jmap.json.PushSerializer
import org.apache.james.util.ReactorUtils
import org.reactivestreams.Publisher
import play.api.libs.json.Json
import reactor.core.scala.publisher.{SFlux, SMono}

case class PushListenerGroup() extends Group {}

class PushListener @Inject()(pushRepository: PushSubscriptionRepository,
                   webPushClient: WebPushClient,
                   pushSerializer: PushSerializer) extends ReactiveGroupEventListener {

  override def getDefaultGroup: Group = PushListenerGroup()

  override def reactiveEvent(event: Event): Publisher[Void] =
    event match {
      case event: StateChangeEvent =>
        SFlux(pushRepository.list(event.username))
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
      .fold(SMono.empty[Unit])(stateChange => SMono(webPushClient.push(pushSubscription.url, asPushRequest(stateChange))))

  private def asPushRequest(stateChange: StateChange): PushRequest =
    PushRequest(ttl = PushTTL.MAX, payload = asBytes(stateChange))

  private def asBytes(stateChange: StateChange) =
    Json.stringify(pushSerializer.serializeSSE(stateChange)).getBytes(StandardCharsets.UTF_8)
}
