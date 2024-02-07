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
package org.apache.james.eventsourcing.eventstore.dto

import org.apache.james.eventsourcing.Event
import org.apache.james.json.DTOModule

object EventDTOModule {
  def forEvent[EventTypeT <: Event](eventType: Class[EventTypeT]) = new DTOModule.Builder[EventTypeT](eventType)
}

case class EventDTOModule[T <: Event, U <: EventDTO](converter: DTOModule.DTOConverter[T, U],
                                                     toDomainObjectConverter: DTOModule.DomainObjectConverter[T, U],
                                                     domainObjectType: Class[T],
                                                     dtoType: Class[U],
                                                     typeName: String) extends DTOModule[T, U](converter, toDomainObjectConverter, domainObjectType, dtoType, typeName) {
  override def toDTO(domainObject: T) : U = super.toDTO(domainObject)
}