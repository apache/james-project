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
package org.apache.james.jmap;

import org.apache.james.backends.cassandra.EmbeddedCassandra;
import org.apache.james.jmap.cassandra.CassandraJmapServer;
import org.apache.james.jmap.utils.ZonedDateTimeProvider;
import org.apache.james.mailbox.elasticsearch.EmbeddedElasticSearch;
import org.junit.rules.TemporaryFolder;

import com.google.inject.util.Modules;

public class CassandraJmapAuthenticationTest extends JMAPAuthenticationTest {

    @Override
    protected JmapServer jmapServer(TemporaryFolder temporaryFolder, EmbeddedElasticSearch embeddedElasticSearch, EmbeddedCassandra cassandra, ZonedDateTimeProvider zonedDateTimeProvider) {
        return new CassandraJmapServer(
                Modules.combine(
                        CassandraJmapServer.defaultOverrideModule(temporaryFolder, embeddedElasticSearch, cassandra),
                        (binder) -> binder.bind(ZonedDateTimeProvider.class).toInstance(zonedDateTimeProvider)));
    }
    
}
