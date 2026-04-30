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

package org.apache.james.jmap.api.identity

import org.apache.james.core.Username
import org.apache.james.events.Event
import org.apache.james.events.Event.EventId
import org.apache.james.jmap.api.model.{Identity, IdentityId}

sealed trait IdentityEvent extends Event {
  def eventId: EventId
  def username: Username

  override def getEventId: EventId = eventId
  override def getUsername: Username = username
  override def isNoop: Boolean = false
}

case class CustomIdentityCreated(eventId: EventId,
                                 username: Username,
                                 identity: Identity) extends IdentityEvent

case class CustomIdentityUpdated(eventId: EventId,
                                 username: Username,
                                 identity: Identity) extends IdentityEvent

case class CustomIdentityDeleted(eventId: EventId,
                                 username: Username,
                                 identityIds: Set[IdentityId]) extends IdentityEvent

case class AllCustomIdentitiesDeleted(eventId: EventId,
                                      username: Username,
                                      identityIds: Set[IdentityId]) extends IdentityEvent
