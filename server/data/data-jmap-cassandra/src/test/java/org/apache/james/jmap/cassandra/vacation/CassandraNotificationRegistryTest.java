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

package org.apache.james.jmap.cassandra.vacation;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.DockerCassandraRule;
import org.apache.james.jmap.api.vacation.AbstractNotificationRegistryTest;
import org.apache.james.jmap.api.vacation.NotificationRegistry;
import org.apache.james.util.date.ZonedDateTimeProvider;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;

public class CassandraNotificationRegistryTest extends AbstractNotificationRegistryTest {

    @ClassRule public static DockerCassandraRule cassandraServer = new DockerCassandraRule();
    
    private static CassandraCluster cassandra;

    @BeforeClass
    public static void setUpClass() {
        cassandra = CassandraCluster.create(CassandraNotificationRegistryModule.MODULE, cassandraServer.getHost());
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() {
        cassandra.clearTables();
    }

    @AfterClass
    public static void tearDownClass() {
        cassandra.closeCluster();
    }
    
    @Override
    protected NotificationRegistry createNotificationRegistry(ZonedDateTimeProvider zonedDateTimeProvider) {
        return new CassandraNotificationRegistry(zonedDateTimeProvider, new CassandraNotificationRegistryDAO(cassandra.getConf()));
    }

}
