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
package org.apache.james.eventsourcing.eventstore.cassandra

import org.apache.james.backends.cassandra.CassandraClusterExtension
import org.apache.james.eventsourcing.eventstore.{EventStore, JsonEventSerializer}
import org.junit.jupiter.api.extension.{AfterAllCallback, AfterEachCallback, BeforeAllCallback, BeforeEachCallback, ExtensionContext, ParameterContext, ParameterResolutionException, ParameterResolver}

class CassandraEventStoreExtension(var cassandra: CassandraClusterExtension, val eventSerializer: JsonEventSerializer)
  extends BeforeAllCallback with AfterAllCallback with BeforeEachCallback with AfterEachCallback with ParameterResolver {

  private var eventStoreDao : Option[EventStoreDao] = None

  def this(eventSerializer: JsonEventSerializer) = this(new CassandraClusterExtension(CassandraEventStoreModule.MODULE), eventSerializer)

  override def beforeAll(context: ExtensionContext): Unit = cassandra.beforeAll(context)

  override def afterAll(context: ExtensionContext): Unit = cassandra.afterAll(context)

  override def beforeEach(context: ExtensionContext): Unit = eventStoreDao =
    Some(new EventStoreDao(cassandra.getCassandraCluster.getConf, eventSerializer))

  override def afterEach(context: ExtensionContext): Unit = cassandra.afterEach(context)

  @throws[ParameterResolutionException]
  override def supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean =
    parameterContext.getParameter.getType eq classOf[EventStore]

  @throws[ParameterResolutionException]
  override def resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): CassandraEventStore =
    new CassandraEventStore(eventStoreDao.get)
}