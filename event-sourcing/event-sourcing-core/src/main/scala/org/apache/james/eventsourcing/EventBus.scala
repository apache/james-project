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
package org.apache.james.eventsourcing

import javax.inject.Inject
import org.apache.james.eventsourcing.eventstore.{EventStore, EventStoreFailedException}
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory
import reactor.core.scala.publisher.{SFlux, SMono}

object EventBus {
  private val LOGGER = LoggerFactory.getLogger(classOf[EventBus])
}

class EventBus @Inject() (eventStore: EventStore, subscribers: Set[Subscriber]) {
  @throws[EventStoreFailedException]
  def publish(events: Iterable[Event]): SMono[Void] =
    SMono(eventStore.appendAll(events))
        .`then`(runHandlers(events, subscribers))

  def runHandlers(events: Iterable[Event], subscribers: Set[Subscriber]): SMono[Void] =
    SFlux.fromIterable(events.flatMap((event: Event) => subscribers.map(subscriber => (event, subscriber))))
      .flatMapSequential(infos => runHandler(infos._1, infos._2))
      .`then`()
      .`then`(SMono.empty)

  def runHandler(event: Event, subscriber: Subscriber): Publisher[Void] =
    SMono(ReactiveSubscriber.asReactiveSubscriber(subscriber).handleReactive(event))
      .onErrorResume(e => {
        EventBus.LOGGER.error("Error while calling {} for {}", subscriber, event, e)
        SMono.empty
      })
}