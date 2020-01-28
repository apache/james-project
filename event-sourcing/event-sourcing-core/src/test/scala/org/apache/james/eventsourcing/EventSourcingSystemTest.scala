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

import com.google.common.base.Splitter
import org.apache.james.eventsourcing.eventstore.{EventStore, History}
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{doThrow, mock, when}
import org.mockito.internal.matchers.InstanceOf
import org.mockito.internal.progress.ThreadSafeMockingProgress

import scala.collection.immutable.List
import scala.jdk.CollectionConverters._

object EventSourcingSystemTest {
  val PAYLOAD_1 = "payload1"
  val PAYLOAD_2 = "payload2"
  val AGGREGATE_ID = TestAggregateId(42)

  class MyCommand(val payload: String) extends Command {
    def getPayload: String = payload
  }

  class EventIdIncrementer(var currentEventId: EventId) {
    def next: EventId = {
      currentEventId = currentEventId.next
      currentEventId
    }
  }

  def anyScalaList[T] : List[T] = {
    ThreadSafeMockingProgress.mockingProgress
      .getArgumentMatcherStorage
      .reportMatcher(new InstanceOf(classOf[List[_]], "<any scala List>"))
    scala.collection.immutable.List.newBuilder[T].result
  }
}

trait EventSourcingSystemTest {
  @Test
  def dispatchShouldApplyCommandHandlerThenCallSubscribers(eventStore: EventStore) : Unit = {
    val subscriber = new DataCollectorSubscriber
    val eventSourcingSystem = new EventSourcingSystem(Set(simpleDispatcher(eventStore)), Set(subscriber), eventStore)
    eventSourcingSystem.dispatch(new EventSourcingSystemTest.MyCommand(EventSourcingSystemTest.PAYLOAD_1))
    assertThat(subscriber.getData.asJava).containsExactly(EventSourcingSystemTest.PAYLOAD_1)
  }

  @Test
  def throwingSubscribersShouldNotAbortSubscriberChain(eventStore: EventStore) : Unit = {
    val subscriber = new DataCollectorSubscriber
    val eventSourcingSystem = new EventSourcingSystem(
      Set(simpleDispatcher(eventStore)),
      Set((_: Event) => throw new RuntimeException, subscriber),
      eventStore)
    eventSourcingSystem.dispatch(new EventSourcingSystemTest.MyCommand(EventSourcingSystemTest.PAYLOAD_1))
    assertThat(subscriber.getData.asJava).containsExactly(EventSourcingSystemTest.PAYLOAD_1)
  }

  @Test
  def throwingStoreShouldNotLeadToPublishing() : Unit = {
    val eventStore = mock(classOf[EventStore])
    doThrow(new RuntimeException).when(eventStore).appendAll(EventSourcingSystemTest.anyScalaList)
    when(eventStore.getEventsOfAggregate(any)).thenReturn(History.empty)
    val subscriber = new DataCollectorSubscriber
    val eventSourcingSystem = new EventSourcingSystem(
      Set(simpleDispatcher(eventStore)),
      Set((_: Event) => throw new RuntimeException, subscriber),
      eventStore)
    assertThatThrownBy(() => eventSourcingSystem.dispatch(new EventSourcingSystemTest.MyCommand(EventSourcingSystemTest.PAYLOAD_1)))
      .isInstanceOf(classOf[RuntimeException])
    assertThat(subscriber.getData.asJava).isEmpty()
  }

  @Test
  def dispatchShouldApplyCommandHandlerThenStoreGeneratedEvents(eventStore: EventStore) : Unit = {
    val subscriber = new DataCollectorSubscriber
    val eventSourcingSystem = new EventSourcingSystem(Set(simpleDispatcher(eventStore)), Set(subscriber), eventStore)
    eventSourcingSystem.dispatch(new EventSourcingSystemTest.MyCommand(EventSourcingSystemTest.PAYLOAD_1))
    val expectedEvent = TestEvent(EventId.first, EventSourcingSystemTest.AGGREGATE_ID, EventSourcingSystemTest.PAYLOAD_1)
    assertThat(eventStore.getEventsOfAggregate(EventSourcingSystemTest.AGGREGATE_ID).getEventsJava).containsOnly(expectedEvent)
  }

  @Test
  def dispatchShouldCallSubscriberForSubsequentCommands(eventStore: EventStore) : Unit = {
    val subscriber = new DataCollectorSubscriber
    val eventSourcingSystem = new EventSourcingSystem(Set(simpleDispatcher(eventStore)), Set(subscriber), eventStore)
    eventSourcingSystem.dispatch(new EventSourcingSystemTest.MyCommand(EventSourcingSystemTest.PAYLOAD_1))
    eventSourcingSystem.dispatch(new EventSourcingSystemTest.MyCommand(EventSourcingSystemTest.PAYLOAD_2))
    assertThat(subscriber.getData.asJava).containsExactly(EventSourcingSystemTest.PAYLOAD_1, EventSourcingSystemTest.PAYLOAD_2)
  }

  @Test
  def dispatchShouldStoreEventsForSubsequentCommands(eventStore: EventStore) : Unit = {
    val subscriber = new DataCollectorSubscriber
    val eventSourcingSystem = new EventSourcingSystem(Set(simpleDispatcher(eventStore)), Set(subscriber), eventStore)
    eventSourcingSystem.dispatch(new EventSourcingSystemTest.MyCommand(EventSourcingSystemTest.PAYLOAD_1))
    eventSourcingSystem.dispatch(new EventSourcingSystemTest.MyCommand(EventSourcingSystemTest.PAYLOAD_2))
    val expectedEvent1 = TestEvent(EventId.first, EventSourcingSystemTest.AGGREGATE_ID, EventSourcingSystemTest.PAYLOAD_1)
    val expectedEvent2 = TestEvent(expectedEvent1.eventId.next, EventSourcingSystemTest.AGGREGATE_ID, EventSourcingSystemTest.PAYLOAD_2)
    assertThat(eventStore.getEventsOfAggregate(EventSourcingSystemTest.AGGREGATE_ID).getEventsJava).containsOnly(expectedEvent1, expectedEvent2)
  }

  @Test
  def dispatcherShouldBeAbleToReturnSeveralEvents(eventStore: EventStore) : Unit = {
    val subscriber = new DataCollectorSubscriber
    val eventSourcingSystem = new EventSourcingSystem(Set(wordCuttingDispatcher(eventStore)), Set(subscriber), eventStore)
    eventSourcingSystem.dispatch(new EventSourcingSystemTest.MyCommand("This is a test"))
    assertThat(subscriber.getData.asJava).containsExactly("This", "is", "a", "test")
  }

  @Test
  def unknownCommandsShouldBeIgnored(eventStore: EventStore) : Unit = {
    val subscriber = new DataCollectorSubscriber
    val eventSourcingSystem = new EventSourcingSystem(Set(wordCuttingDispatcher(eventStore)), Set(subscriber), eventStore)
    assertThatThrownBy(() => eventSourcingSystem.dispatch(new Command() {}))
      .isInstanceOf(classOf[CommandDispatcher.UnknownCommandException])
  }

  @Test
  def constructorShouldThrowWhenSeveralHandlersForTheSameCommand(eventStore: EventStore) : Unit = {
    val subscriber = new DataCollectorSubscriber
    assertThatThrownBy(() => new EventSourcingSystem(
      Set(wordCuttingDispatcher(eventStore), simpleDispatcher(eventStore)),
      Set(subscriber), eventStore))
      .isInstanceOf(classOf[IllegalArgumentException])
  }

  def simpleDispatcher(eventStore: EventStore) = new CommandHandler[EventSourcingSystemTest.MyCommand]() {
    override def handledClass: Class[EventSourcingSystemTest.MyCommand] = classOf[EventSourcingSystemTest.MyCommand]

    override def handle(myCommand: EventSourcingSystemTest.MyCommand): List[TestEvent] = {
      val history = eventStore.getEventsOfAggregate(EventSourcingSystemTest.AGGREGATE_ID)
      List(TestEvent(history.getNextEventId, EventSourcingSystemTest.AGGREGATE_ID, myCommand.getPayload))
    }
  }

  def wordCuttingDispatcher(eventStore: EventStore) = new CommandHandler[EventSourcingSystemTest.MyCommand]() {
    override def handledClass: Class[EventSourcingSystemTest.MyCommand] = classOf[EventSourcingSystemTest.MyCommand]

    override def handle(myCommand: EventSourcingSystemTest.MyCommand): List[TestEvent] = {
      val history = eventStore.getEventsOfAggregate(EventSourcingSystemTest.AGGREGATE_ID)
      val eventIdIncrementer = new EventSourcingSystemTest.EventIdIncrementer(history.getNextEventId)
      Splitter.on(" ").splitToList(myCommand.getPayload)
        .asScala
        .toList
        .map((word: String) => TestEvent(eventIdIncrementer.next, EventSourcingSystemTest.AGGREGATE_ID, word))
    }
  }
}