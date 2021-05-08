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
 * ***************************************************************/
package org.apache.james.eventsourcing

import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono
import reactor.core.scheduler.Schedulers

trait Subscriber {
  def handle(event: Event) : Unit
}

trait ReactiveSubscriber extends Subscriber {
  def handleReactive(event: Event): Publisher[Void]

  override def handle(event: Event) : Unit = SMono(handleReactive(event)).block()
}

object ReactiveSubscriber {
  def asReactiveSubscriber(subscriber: Subscriber): ReactiveSubscriber = subscriber match {
    case reactive: ReactiveSubscriber => reactive
    case nonReactive => new ReactiveSubscriberWrapper(nonReactive)
  }
}

class ReactiveSubscriberWrapper(delegate: Subscriber) extends ReactiveSubscriber {
  override def handle(event: Event) : Unit = delegate.handle(event)

  def handleReactive(event: Event): Publisher[Void] = SMono.fromCallable(() => handle(event))
    .subscribeOn(Schedulers.elastic())
    .`then`()
}