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