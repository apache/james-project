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

package org.apache.james.jmap.metrics

import io.micrometer.core.instrument.Metrics.globalRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import jakarta.inject.{Inject, Singleton}
import org.apache.james.jmap.metrics.HttpClientMetrics.{NETTY_CONNECTIONS_ACTIVE, NETTY_CONNECTIONS_TOTAL, NETTY_DATA_RECEIVED, NETTY_DATA_SENT}
import org.apache.james.metrics.api.GaugeRegistry
import reactor.netty.Metrics.{CONNECTIONS_ACTIVE, CONNECTIONS_TOTAL, DATA_RECEIVED, DATA_SENT, HTTP_SERVER_PREFIX}

object HttpClientMetrics {
  lazy val NETTY_CONNECTIONS_ACTIVE: String = HTTP_SERVER_PREFIX + CONNECTIONS_ACTIVE
  lazy val NETTY_CONNECTIONS_TOTAL: String = HTTP_SERVER_PREFIX + CONNECTIONS_TOTAL
  lazy val NETTY_DATA_RECEIVED: String = HTTP_SERVER_PREFIX + DATA_RECEIVED
  lazy val NETTY_DATA_SENT: String = HTTP_SERVER_PREFIX + DATA_SENT
}

@Singleton
case class HttpClientMetrics @Inject()(gaugeRegistry: GaugeRegistry) {
  private lazy val activeConnectionGauge: GaugeRegistry.SettableGauge[Integer] = gaugeRegistry.settableGauge(s"jmap.$NETTY_CONNECTIONS_ACTIVE")
  private lazy val totalConnectionGauge: GaugeRegistry.SettableGauge[Integer] = gaugeRegistry.settableGauge(s"jmap.$NETTY_CONNECTIONS_TOTAL")
  private lazy val dataReceivedGauge: GaugeRegistry.SettableGauge[Integer] = gaugeRegistry.settableGauge(s"jmap.$NETTY_DATA_RECEIVED")
  private lazy val dataSentGauge: GaugeRegistry.SettableGauge[Integer] = gaugeRegistry.settableGauge(s"jmap.$NETTY_DATA_SENT")
  private lazy val nettyCompositeMeterRegistry = globalRegistry.add(new SimpleMeterRegistry())

  def update(): Unit = {
    updateActiveConnectionGauge()
    updateTotalConnectionGauge()
    updateDateReceivedGauge()
    updateDataSentGauge()
  }

  private def updateActiveConnectionGauge(): Unit =
    Option(nettyCompositeMeterRegistry.find(NETTY_CONNECTIONS_ACTIVE))
      .flatMap(search => Option(search.gauge()))
      .flatMap(gauge => Option(gauge.value()))
      .foreach(double => activeConnectionGauge.setValue(double.intValue))

  private def updateTotalConnectionGauge(): Unit =
    Option(nettyCompositeMeterRegistry.find(NETTY_CONNECTIONS_TOTAL))
      .flatMap(search => Option(search.gauge()))
      .flatMap(gauge => Option(gauge.value()))
      .foreach(double => totalConnectionGauge.setValue(double.intValue))

  private def updateDateReceivedGauge(): Unit =
    Option(nettyCompositeMeterRegistry.find(NETTY_DATA_RECEIVED))
      .flatMap(search => Option(search.summary()))
      .flatMap(summary => Option(summary.totalAmount()))
      .foreach(double => dataReceivedGauge.setValue(double.intValue))

  private def updateDataSentGauge(): Unit =
    Option(nettyCompositeMeterRegistry.find(NETTY_DATA_SENT))
      .flatMap(search => Option(search.summary()))
      .flatMap(summary => Option(summary.totalAmount()))
      .foreach(double => dataSentGauge.setValue(double.intValue))
}
