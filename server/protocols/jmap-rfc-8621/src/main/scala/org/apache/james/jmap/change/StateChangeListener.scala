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

package org.apache.james.jmap.change

import java.nio.charset.StandardCharsets

import org.apache.james.events.Event
import org.apache.james.events.EventListener.ReactiveEventListener
import org.apache.james.jmap.json.ResponseSerializer
import org.reactivestreams.Publisher
import play.api.libs.json.Json
import reactor.core.scala.publisher.SMono
import reactor.core.scheduler.Schedulers
import reactor.netty.http.websocket.WebsocketOutbound

case class StateChangeListener(types: Set[TypeName], outbound: WebsocketOutbound) extends ReactiveEventListener {
  override def reactiveEvent(event: Event): Publisher[Void] = event match {
    case stateChangeEvent: StateChangeEvent =>
      val stateChange = stateChangeEvent.asStateChange.filter(types)
      val jsonString = stateChange.map(ResponseSerializer.serialize).map(Json.stringify)
      jsonString.map(json => SMono(outbound.sendString(SMono.just[String](json), StandardCharsets.UTF_8))
        .subscribeOn(Schedulers.elastic))
        .getOrElse(SMono.empty)
    case _ => SMono.empty
  }

  override def isHandling(event: Event): Boolean = event match {
    case _: StateChangeEvent => true
    case _ => false
  }
}
