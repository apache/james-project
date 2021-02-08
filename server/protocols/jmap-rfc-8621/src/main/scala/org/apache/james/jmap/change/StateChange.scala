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
import org.apache.james.jmap.api.change.{State => JavaState}
import org.apache.james.jmap.core.{AccountId, PushState, State, StateChange}

object TypeName {
  val ALL: Set[TypeName] = Set(EmailTypeName, MailboxTypeName, ThreadTypeName, IdentityTypeName, EmailSubmissionTypeName, EmailDeliveryTypeName)

  def parse(string: String): Either[String, TypeName] = string match {
    case MailboxTypeName.asString => Right(MailboxTypeName)
    case EmailTypeName.asString => Right(EmailTypeName)
    case ThreadTypeName.asString => Right(ThreadTypeName)
    case IdentityTypeName.asString => Right(IdentityTypeName)
    case EmailSubmissionTypeName.asString => Right(EmailSubmissionTypeName)
    case EmailDeliveryTypeName.asString => Right(EmailDeliveryTypeName)
    case VacationResponseTypeName.asString => Right(VacationResponseTypeName)
    case _ => Left(s"Unknown typeName $string")
  }
}

sealed trait TypeName {
  def asMap(maybeState: Option[State]): Map[TypeName, State] =
    maybeState.map(state => Map[TypeName, State](this -> state))
      .getOrElse(Map())

  def asString(): String
}
case object MailboxTypeName extends TypeName {
  override val asString: String = "Mailbox"
}
case object EmailTypeName extends TypeName {
  override val asString: String = "Email"
}
case object ThreadTypeName extends TypeName {
  override val asString: String = "Thread"
}
case object IdentityTypeName extends TypeName {
  override val asString: String = "Identity"
}
case object EmailSubmissionTypeName extends TypeName {
  override val asString: String = "EmailSubmission"
}
case object EmailDeliveryTypeName extends TypeName {
  override val asString: String = "EmailDelivery"
}
case object VacationResponseTypeName extends TypeName {
  override val asString: String = "VacationResponse"
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
                            vacationResponseState: Option[State],
                            mailboxState: Option[State],
                            emailState: Option[State],
                            emailDeliveryState: Option[State]) extends Event {
  def asStateChange: StateChange =
    StateChange(Map(AccountId.from(username).fold(
      failure => throw new IllegalArgumentException(failure),
      success => success) ->
      TypeState(
        VacationResponseTypeName.asMap(vacationResponseState) ++
          MailboxTypeName.asMap(mailboxState) ++
          EmailDeliveryTypeName.asMap(emailDeliveryState) ++
          EmailTypeName.asMap(emailState))),
      PushState.fromOption(
        mailboxState.map(state => JavaState.of(state.value)),
        emailState.map(state => JavaState.of(state.value))))

  override val getUsername: Username = username

  override val isNoop: Boolean = mailboxState.isEmpty &&
    emailState.isEmpty &&
    vacationResponseState.isEmpty &&
    emailDeliveryState.isEmpty

  override val getEventId: EventId = eventId
}