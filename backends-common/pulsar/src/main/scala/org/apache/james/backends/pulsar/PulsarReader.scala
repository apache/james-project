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

package org.apache.james.backends.pulsar


import scala.concurrent.{ExecutionContext, Future}

import org.apache.pulsar.client.api.Schema

import com.sksamuel.pulsar4s.{ConsumerMessage, Message, MessageId, PulsarClient, Reader, ReaderConfig, SequenceId, Topic}

import org.apache.pekko.Done
import org.apache.pekko.stream.scaladsl.Source


object PulsarReader {
  object schemas {
    implicit val schema: Schema[String] = Schema.STRING
  }

  def forTopic(topic: Topic, maxSequenceId: Option[SequenceId] = None)(implicit client: PulsarClient, executionContext: ExecutionContext) = {
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
      .takeWhile(message => maxSequenceId.forall(max => message.sequenceId.value < max.value), inclusive = true)
  }

}
