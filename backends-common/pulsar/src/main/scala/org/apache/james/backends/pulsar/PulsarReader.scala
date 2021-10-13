package org.apache.james.backends.pulsar

import akka.Done
import com.sksamuel.pulsar4s.{ConsumerMessage, Message, MessageId, PulsarClient, Reader, ReaderConfig, Topic}
import akka.stream.scaladsl.Source
import org.apache.pulsar.client.api.Schema

import scala.concurrent.{ExecutionContext, Future}

object PulsarReader {
  object schemas {
    implicit val schema: Schema[String] = Schema.STRING
  }
  def forTopic(topic: Topic)(implicit client: PulsarClient, executionContext: ExecutionContext) = {
    import schemas.schema
    Source.unfoldResourceAsync[ConsumerMessage[String], Reader[String]](
      create = () => {
        Future.successful(
          client.reader(
            config = ReaderConfig(topic, startMessage = Message(MessageId.earliest))
          )
        )
      },
      read = reader => {
        if (reader.hasMessageAvailable) reader.nextAsync.map(Some(_))
        else Future.successful(None)
      },
      close = reader => reader.closeAsync.map(_ => Done))
  }

}
