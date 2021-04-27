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

import org.apache.james.core.Username
import org.apache.james.events.Event
import org.apache.james.events.Event.EventId
import org.apache.james.jmap.core.{AccountId, PushState, State, StateChange, UuidState}

trait TypeName {
  def asMap(maybeState: Option[State]): Map[TypeName, State] =
    maybeState.map(state => Map[TypeName, State](this -> state))
      .getOrElse(Map())

  def asString(): String
  def parse(string: String): Option[TypeName]
  def parseState(string: String): Either[IllegalArgumentException, State]
}
case object MailboxTypeName extends TypeName {
  override val asString: String = "Mailbox"

  override def parse(string: String): Option[TypeName] = string match {
    case MailboxTypeName.asString => Some(MailboxTypeName)
    case _ => None
  }

  override def parseState(string: String): Either[IllegalArgumentException, UuidState] = UuidState.parse(string)
}
case object EmailTypeName extends TypeName {
  override val asString: String = "Email"

  override def parse(string: String): Option[TypeName] = string match {
    case EmailTypeName.asString => Some(EmailTypeName)
    case _ => None
  }

  override def parseState(string: String): Either[IllegalArgumentException, UuidState] = UuidState.parse(string)
}
case object ThreadTypeName extends TypeName {
  override val asString: String = "Thread"

  override def parse(string: String): Option[TypeName] = string match {
    case ThreadTypeName.asString => Some(ThreadTypeName)
    case _ => None
  }

  override def parseState(string: String): Either[IllegalArgumentException, UuidState] = UuidState.parse(string)
}
case object IdentityTypeName extends TypeName {
  override val asString: String = "Identity"

  override def parse(string: String): Option[TypeName] = string match {
    case IdentityTypeName.asString => Some(IdentityTypeName)
    case _ => None
  }

  override def parseState(string: String): Either[IllegalArgumentException, UuidState] = UuidState.parse(string)
}
case object EmailSubmissionTypeName extends TypeName {
  override val asString: String = "EmailSubmission"

  override def parse(string: String): Option[TypeName] = string match {
    case EmailSubmissionTypeName.asString => Some(EmailSubmissionTypeName)
    case _ => None
  }

  override def parseState(string: String): Either[IllegalArgumentException, UuidState] = UuidState.parse(string)
}
case object EmailDeliveryTypeName extends TypeName {
  override val asString: String = "EmailDelivery"

  override def parse(string: String): Option[TypeName] = string match {
    case EmailDeliveryTypeName.asString => Some(EmailDeliveryTypeName)
    case _ => None
  }

  override def parseState(string: String): Either[IllegalArgumentException, UuidState] = UuidState.parse(string)
}
case object VacationResponseTypeName extends TypeName {
  override val asString: String = "VacationResponse"

  override def parse(string: String): Option[TypeName] = string match {
    case VacationResponseTypeName.asString => Some(VacationResponseTypeName)
    case _ => None
  }

  override def parseState(string: String): Either[IllegalArgumentException, UuidState] = UuidState.parse(string)
}

case class TypeState(changes: Map[TypeName, State]) {

  def filter(types: Set[TypeName]): Option[TypeState] = Option(changes.filter {
    case (typeName, _) => types.contains(typeName)
  })
    .filter(_.nonEmpty)
    .map(TypeState)
}

case class StateChangeEvent(eventId: EventId,
                            username: Username,
                            map: Map[TypeName, State]) extends Event {
  def asStateChange: StateChange = {
    StateChange(Map(AccountId.from(username).fold(
      failure => throw new IllegalArgumentException(failure),
      success => success) ->
      TypeState(map)),
      PushState.fromOption(getState(MailboxTypeName), getState(EmailTypeName)))
  }

  def getState(typeName: TypeName): Option[State] =
    map.find(element => element._1.equals(typeName)).map(element => element._2)

  override val getUsername: Username = username

  override val isNoop: Boolean = map.isEmpty

  override val getEventId: EventId = eventId
}