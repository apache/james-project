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

import org.apache.james.util.ReactorUtils
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

trait Subscriber {
  def handle(commandExecuted: EventWithState) : Unit
}

trait ReactiveSubscriber extends Subscriber {
  def handleReactive(commandExecuted: EventWithState): Publisher[Void]

  override def handle(commandExecuted: EventWithState) : Unit = SMono(handleReactive(commandExecuted)).block()
}

object ReactiveSubscriber {
  def asReactiveSubscriber(subscriber: Subscriber): ReactiveSubscriber = subscriber match {
    case reactive: ReactiveSubscriber => reactive
    case nonReactive => new ReactiveSubscriberWrapper(nonReactive)
  }
}

class ReactiveSubscriberWrapper(delegate: Subscriber) extends ReactiveSubscriber {
  override def handle(commandExecuted: EventWithState) : Unit = delegate.handle(commandExecuted)

  def handleReactive(commandExecuted: EventWithState): Publisher[Void] = SMono.fromCallable(() => handle(commandExecuted))
    .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
    .`then`()
}