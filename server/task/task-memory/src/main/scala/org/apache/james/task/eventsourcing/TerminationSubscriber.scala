 /***************************************************************
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

package org.apache.james.task.eventsourcing

import org.apache.james.eventsourcing.{Event, EventWithState, ReactiveSubscriber}
import org.reactivestreams.Publisher
import reactor.core.publisher.Sinks.EmitFailureHandler.FAIL_FAST
import reactor.core.publisher.{Mono, Sinks}

trait TerminationSubscriber extends ReactiveSubscriber {
  override def handleReactive(event: EventWithState): Publisher[Void] = Mono.fromRunnable(() => handle(event))

  override def handle(event: EventWithState): Unit = event.event match {
    case event: TerminalTaskEvent => addEvent(event)
    case _ =>
  }

  def addEvent(event: Event): Unit

  def listenEvents: Publisher[Event]
}

class MemoryTerminationSubscriber extends TerminationSubscriber {
  private val events: Sinks.Many[Event] = Sinks.many().multicast().directBestEffort()

  override def addEvent(event: Event): Unit =
    events.emitNext(event, FAIL_FAST)

  override def listenEvents: Publisher[Event] =
    events.asFlux()
}