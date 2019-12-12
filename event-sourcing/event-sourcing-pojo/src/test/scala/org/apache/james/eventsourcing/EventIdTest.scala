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

import nl.jqno.equalsverifier.EqualsVerifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class EventIdTest {
  @Test
  def shouldMatchBeanContract(): Unit = EqualsVerifier.forClass(classOf[EventId]).verify()

  @Test
  def firstShouldReturnAConstant(): Unit =
    assertThat(EventId.first)
    .isEqualTo(EventId.first)

  @Test
  def previousShouldReturnEmptyWhenBeforeFirst(): Unit =
    assertThat(EventId.first.previous)
      .isEqualTo(None)

  @Test
  def compareToShouldReturnNegativeWhenComparedToNext(): Unit =
    assertThat(EventId.first)
      .isLessThan(EventId.first.next)

  @Test
  def compareToShouldReturnNegativeWhenComparedToPrevious(): Unit =
    assertThat(EventId.first.next)
      .isGreaterThan(EventId.first)

  @Test
  def nextShouldAlwaysHaveTheSameIncrement(): Unit =
    assertThat(EventId.first.next)
      .isEqualTo(EventId.first.next)

  @Test
  def previousShouldRevertNext(): Unit =
    assertThat(EventId.first.next.previous)
      .isEqualTo(Some(EventId.first))

  @Test
  def compareToShouldReturnNegativeWhenComparedToNextWithPreviousCall(): Unit =
    assertThat(EventId.first.next.previous.get)
    .isLessThan(EventId.first.next)

  @Test
  def compareToShouldReturnNegativeWhenComparedToPreviousWithPreviousCall(): Unit =
    assertThat(EventId.first.next)
    .isGreaterThan(EventId.first.next.previous.get)
}