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

import com.sksamuel.pulsar4s.{PulsarAsyncClient, PulsarClient, PulsarClientConfig}
import org.apache.commons.io.IOUtils
import org.apache.pulsar.client.admin.PulsarAdmin
import org.apache.pulsar.client.impl.auth.{AuthenticationBasic, AuthenticationDisabled, AuthenticationToken}

import java.io.Closeable
import javax.annotation.PreDestroy
import scala.jdk.CollectionConverters.MapHasAsJava

object PulsarClients {
  def create(configuration: PulsarConfiguration): PulsarClients = {
    val pulsarAuth = configuration.auth match {
      case Auth.NoAuth => new AuthenticationDisabled()
      case Auth.Token(value) => new AuthenticationToken(value)
      case Auth.Basic(userId, password) =>
        val basic = new AuthenticationBasic()
        basic.configure(Map("userId" -> userId, "password" -> password).asJava)
        basic
    }

    val adminClient: PulsarAdmin =
      PulsarAdmin.builder()
        .serviceHttpUrl(configuration.adminUri)
        .authentication(pulsarAuth)
        .build()

    val asyncClient: PulsarAsyncClient = {
      PulsarClient(
        PulsarClientConfig(
          serviceUrl = configuration.brokerUri,
          authentication = Some(pulsarAuth)
        )
      )
    }

    new PulsarClients(adminClient, asyncClient)
  }
}


class PulsarClients(val adminClient: PulsarAdmin, val asyncClient: PulsarAsyncClient) {
  @PreDestroy
  def stop(): Unit = {
    val closeableAsyncClient: Closeable = () => asyncClient.close()
    IOUtils.closeQuietly(adminClient, closeableAsyncClient)
  }
}
