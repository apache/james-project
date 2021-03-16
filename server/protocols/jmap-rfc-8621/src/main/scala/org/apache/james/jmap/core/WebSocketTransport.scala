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

package org.apache.james.jmap.core

import java.nio.charset.StandardCharsets

import com.google.common.hash.Hashing
import org.apache.james.jmap.api.change.State
import org.apache.james.jmap.change.{TypeName, TypeState}
import org.apache.james.jmap.http.Authenticator.Authorization
import org.apache.james.jmap.routes.PingPolicy.Interval

sealed trait WebSocketInboundMessage

sealed trait OutboundMessage

case class PingMessage(interval: Interval) extends OutboundMessage

case class RequestId(value: String) extends AnyVal

case class WebSocketRequest(requestId: Option[RequestId], requestObject: RequestObject) extends WebSocketInboundMessage

case class WebSocketResponse(requestId: Option[RequestId], responseObject: ResponseObject) extends OutboundMessage

case class WebSocketError(requestId: Option[RequestId], problemDetails: ProblemDetails) extends OutboundMessage

object PushState {
  def from(mailboxState: State, emailState: State): PushState =
    PushState(hashStates(List(mailboxState, emailState)))

  def fromOption(mailboxState: Option[State], emailState: Option[State]): Option[PushState] =
    List(mailboxState, emailState).flatten match {
      case Nil => None
      case states => Some(PushState(hashStates(states)))
    }

  private def hashStates(states: List[State]): String = Hashing.sha256().hashString(states.mkString("_"), StandardCharsets.UTF_8).toString
}

case class PushState(value: String)

case class StateChange(changes: Map[AccountId, TypeState], pushState: Option[PushState]) extends OutboundMessage {

  def filter(types: Set[TypeName]): Option[StateChange] =
    Option(changes.flatMap {
      case (accountId, typeState) => typeState.filter(types).map(typeState => (accountId, typeState))
    })
    .filter(_.nonEmpty)
    .map(changes => StateChange(changes, pushState))
}

case class WebSocketPushEnable(dataTypes: Option[Set[TypeName]], pushState: Option[PushState]) extends WebSocketInboundMessage
case object WebSocketPushDisable extends WebSocketInboundMessage
case class WebsocketAuthorization(authorization: String) extends WebSocketInboundMessage {
  val asJava: Authorization = new Authorization(authorization)
}
