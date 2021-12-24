/** **************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 * ************************************************************** */

package org.apache.james.queue.pulsar

import akka.actor.{Actor, ActorLogging, Props}
import com.sksamuel.pulsar4s.akka.streams.CommittableMessage
import com.sksamuel.pulsar4s.{ConsumerMessage, SequenceId}

import scala.math.Ordered.orderingToOrdered

private[pulsar] class FilterStage extends Actor with ActorLogging {
  private var filters = Set.empty[Filter]
  private val name = self.path.name

  def receive: Receive = {

    case filter: Filter =>
      registerFilter(filter)
      log.debug(s"new filter registered, active filters : $filters ")

    // processing mail
    case (metadata: MailMetadata, cm: CommittableMessage[_]) =>
      val sequenceId = cm.message.sequenceId
      log.debug(s"filtering mail with active filters : $filters , sequence: ${sequenceId} metadata : $metadata")
      if (shouldBeFiltered(metadata, sequenceId)) {
        log.debug(s"message filtered : sequence: ${sequenceId} metadata : $metadata")
        sender() ! (None, cm)
      } else {
        log.debug(s"message not filtered : sequence: ${sequenceId} metadata : $metadata")
        sender() ! (Some(metadata), cm)
      }
      removeExpiredFilters(sequenceId)

    // support browsing
    case (metadata: MailMetadata, message: ConsumerMessage[_]) =>
      val sequenceId = message.sequenceId
      log.debug(s"filtering browse with filters : $filters , sequence: ${sequenceId} metadata : $metadata")
      if (shouldBeFiltered(metadata, sequenceId)) {
        log.debug(s"message filtered : sequence: ${sequenceId} metadata : $metadata")
        sender() ! None
      } else {
        log.debug(s"message not filtered : sequence: ${sequenceId} metadata : $metadata")
        sender() ! Some(metadata)
      }

    case e =>
      log.error(s"$name received unexpected message", e)
  }

  implicit val seqIdOrder: Ordering[SequenceId] = Ordering.by[SequenceId, Long](s => s.value)

  private def shouldBeFiltered(metadata: MailMetadata, sequenceId: SequenceId) =
    filters.exists(f => f.matches(metadata) && sequenceId <= f.lastSequenceId)

  private def removeExpiredFilters(sequenceId: SequenceId) =
    filters = filters.filter(f => f.lastSequenceId >= sequenceId)

  private def registerFilter(filter: Filter) =
    filters = filters + filter

}

private[pulsar] object FilterStage {
  def props = Props(new FilterStage())
}