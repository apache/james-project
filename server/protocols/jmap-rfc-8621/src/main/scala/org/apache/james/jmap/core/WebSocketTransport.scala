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

import org.apache.james.jmap.change.{TypeName, TypeState}

sealed trait WebSocketInboundMessage

sealed trait WebSocketOutboundMessage

case class RequestId(value: String) extends AnyVal

case class WebSocketRequest(requestId: Option[RequestId], requestObject: RequestObject) extends WebSocketInboundMessage

case class WebSocketResponse(requestId: Option[RequestId], responseObject: ResponseObject) extends WebSocketOutboundMessage

case class WebSocketError(requestId: Option[RequestId], problemDetails: ProblemDetails) extends WebSocketOutboundMessage

case class StateChange(changes: Map[AccountId, TypeState]) extends WebSocketOutboundMessage {

  def filter(types: Set[TypeName]): Option[StateChange] =
    Option(changes.flatMap {
      case (accountId, typeState) => typeState.filter(types).map(typeState => (accountId, typeState))
    })
    .filter(_.nonEmpty)
    .map(StateChange)
}

case class WebSocketPushEnable(dataTypes: Set[TypeName]) extends WebSocketInboundMessage