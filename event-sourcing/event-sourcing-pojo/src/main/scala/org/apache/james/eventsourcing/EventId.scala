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
package org.apache.james.eventsourcing

import com.google.common.base.Preconditions

object EventId {
  def fromSerialized(value: Int): EventId = {
    Preconditions.checkArgument(value >= 0, "EventId can not be negative".asInstanceOf[Object])
    new EventId(value)
  }

  def first: EventId = new EventId(0)
}

final case class EventId private(value: Int) extends Comparable[EventId] {

  def next = new EventId(value + 1)

  def previous: Option[EventId] = Some(value).filter(_ > 0).map(value => EventId(value - 1))

  override def compareTo(o: EventId): Int = value.compareTo(o.value)

  def serialize: Int = value
}