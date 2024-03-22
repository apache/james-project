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

import java.time.Clock;
import java.util.Set;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.cassandra.CassandraBlobStoreFactory;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStore;
import org.apache.james.eventsourcing.eventstore.cassandra.EventNestedTypes;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTO;
import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTOModule;
import org.apache.james.json.DTO;
import org.apache.james.json.DTOModule;
import org.apache.james.mailbox.AttachmentIdFactory;
import org.apache.james.mailbox.StringBackedAttachmentIdFactory;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.ACLMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraACLMapper;
import org.apache.james.mailbox.cassandra.mail.CassandraModSeqProvider;
import org.apache.james.mailbox.cassandra.mail.CassandraUidProvider;
import org.apache.james.mailbox.cassandra.mail.eventsourcing.acl.ACLModule;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.BatchSizes;
import org.apache.james.mailbox.store.mail.ModSeqProvider;
import org.apache.james.mailbox.store.mail.UidProvider;
import org.apache.james.metrics.tests.RecordingMetricFactory;

import com.datastax.oss.driver.api.core.CqlSession;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import com.google.inject.util.Modules;

public class GuiceUtils {
    public static Injector testInjector(CassandraCluster cluster) {
        CqlSession session = cluster.getConf();
        CassandraTypesProvider typesProvider = cluster.getTypesProvider();
        CassandraMessageId.Factory messageIdFactory = new CassandraMessageId.Factory();
        CassandraConfiguration configuration = CassandraConfiguration.DEFAULT_CONFIGURATION;

        return testInjector(session, typesProvider, messageIdFactory, configuration);
    }

    public static Injector testInjector(CqlSession session, CassandraTypesProvider typesProvider,
                                        CassandraMessageId.Factory messageIdFactory,
                                        CassandraConfiguration configuration) {
        return Guice.createInjector(
            commonModules(session, typesProvider, messageIdFactory, configuration));
    }

    public static Module commonModules(CqlSession session, CassandraTypesProvider typesProvider,
                                        CassandraMessageId.Factory messageIdFactory,
                                        CassandraConfiguration configuration) {
        return Modules.combine(
            binder -> binder.bind(MessageId.Factory.class).toInstance(messageIdFactory),
            binder -> binder.bind(BatchSizes.class).toInstance(BatchSizes.defaultValues()),
            binder -> binder.bind(UidProvider.class).to(CassandraUidProvider.class),
            binder -> binder.bind(ModSeqProvider.class).to(CassandraModSeqProvider.class),
            binder -> binder.bind(ACLMapper.class).to(CassandraACLMapper.class),
            binder -> binder.bind(BlobId.Factory.class).toInstance(new HashBlobId.Factory()),
            binder -> binder.bind(BlobStore.class).toProvider(() -> CassandraBlobStoreFactory.forTesting(session, new RecordingMetricFactory()).passthrough()),
            binder -> binder.bind(CqlSession.class).toInstance(session),
            binder -> Multibinder.newSetBinder(binder, new TypeLiteral<EventDTOModule<? extends Event, ? extends EventDTO>>() {})
                .addBinding().toInstance(ACLModule.ACL_UPDATE),
            binder -> binder.bind(new TypeLiteral<Set<DTOModule<?, ? extends DTO>>>() {}).annotatedWith(Names.named(EventNestedTypes.EVENT_NESTED_TYPES_INJECTION_NAME))
                .toInstance(ImmutableSet.of()),
            binder -> Multibinder.newSetBinder(binder, new TypeLiteral<EventDTOModule<? extends Event, ? extends EventDTO>>() {}),
            binder -> binder.bind(EventStore.class).to(CassandraEventStore.class),
            binder -> binder.bind(CassandraTypesProvider.class).toInstance(typesProvider),
            binder -> binder.bind(CassandraConfiguration.class).toInstance(configuration),
            binder -> binder.bind(Clock.class).toInstance(Clock.systemUTC()),
            binder -> binder.bind(AttachmentIdFactory.class).to(StringBackedAttachmentIdFactory.class));
    }
}
