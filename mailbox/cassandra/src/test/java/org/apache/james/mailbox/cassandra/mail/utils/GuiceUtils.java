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

package org.apache.james.mailbox.cassandra.mail.utils;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.init.configuration.CassandraConsistenciesConfiguration;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.cassandra.CassandraBlobStoreFactory;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraACLDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraACLDAOV1;
import org.apache.james.mailbox.model.MessageId;

import com.datastax.driver.core.Session;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Modules;

public class GuiceUtils {
    public static Injector testInjector(CassandraCluster cluster) {
        Session session = cluster.getConf();
        CassandraTypesProvider typesProvider = cluster.getTypesProvider();
        CassandraMessageId.Factory messageIdFactory = new CassandraMessageId.Factory();
        CassandraConfiguration configuration = CassandraConfiguration.DEFAULT_CONFIGURATION;

        return testInjector(session, typesProvider, messageIdFactory, configuration);
    }

    public static Injector testInjector(Session session, CassandraTypesProvider typesProvider,
                                        CassandraMessageId.Factory messageIdFactory,
                                        CassandraConfiguration configuration) {
        return Guice.createInjector(
            commonModules(session, typesProvider, messageIdFactory, configuration));
    }

    private static Module commonModules(Session session, CassandraTypesProvider typesProvider,
                                        CassandraMessageId.Factory messageIdFactory,
                                        CassandraConfiguration configuration) {
        return Modules.combine(
            binder -> binder.bind(CassandraACLDAO.class).to(CassandraACLDAOV1.class),
            binder -> binder.bind(MessageId.Factory.class).toInstance(messageIdFactory),
            binder -> binder.bind(BlobId.Factory.class).toInstance(new HashBlobId.Factory()),
            binder -> binder.bind(BlobStore.class).toProvider(() -> CassandraBlobStoreFactory.forTesting(session).passthrough()),
            binder -> binder.bind(Session.class).toInstance(session),
            binder -> binder.bind(CassandraTypesProvider.class).toInstance(typesProvider),
            binder -> binder.bind(CassandraConfiguration.class).toInstance(configuration),
            binder -> binder.bind(CassandraConsistenciesConfiguration.class)
                .toInstance(CassandraConsistenciesConfiguration.fromConfiguration(configuration)));
    }
}
