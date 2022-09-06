package org.apache.james.queue.pulsar

import org.apache.james.backends.pulsar.PulsarConfiguration
import org.apache.james.queue.api.MailQueueName

case class PulsarMailQueueConfiguration(
  name: MailQueueName,
  pulsar: PulsarConfiguration,
  maxEnqueueConcurrency: Int = 10,
  enqueueBufferSize: Int = 10,
  requeueBufferSize: Int = 10
)
