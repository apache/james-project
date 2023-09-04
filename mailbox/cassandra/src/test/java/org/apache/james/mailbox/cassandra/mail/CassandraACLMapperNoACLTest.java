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
package org.apache.james.mailbox.cassandra.mail;

import static org.apache.james.mailbox.cassandra.mail.CassandraACLMapperContract.MAILBOX_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.StatementRecorder;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.init.configuration.CassandraConfiguration;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionDAO;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStore;
import org.apache.james.eventsourcing.eventstore.cassandra.CassandraEventStoreModule;
import org.apache.james.eventsourcing.eventstore.cassandra.EventStoreDao;
import org.apache.james.eventsourcing.eventstore.cassandra.JsonEventSerializer;
import org.apache.james.mailbox.cassandra.mail.eventsourcing.acl.ACLModule;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.model.MailboxACL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class CassandraACLMapperNoACLTest {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(CassandraAclModule.MODULE, CassandraSchemaVersionModule.MODULE, CassandraEventStoreModule.MODULE()));

    private CassandraACLMapper cassandraACLMapper;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        CassandraSchemaVersionDAO schemaVersionDAO = new CassandraSchemaVersionDAO(cassandra.getConf());
        schemaVersionDAO.truncateVersion().block();
        schemaVersionDAO.updateVersion(new SchemaVersion(9)).block();
        CassandraACLDAOV2 aclDAOv2 = new CassandraACLDAOV2(cassandra.getConf());
        JsonEventSerializer jsonEventSerializer = JsonEventSerializer
            .forModules(ACLModule.ACL_UPDATE)
            .withoutNestedType();
        CassandraUserMailboxRightsDAO usersRightDAO = new CassandraUserMailboxRightsDAO(cassandra.getConf());
        CassandraEventStore eventStore = new CassandraEventStore(new EventStoreDao(cassandra.getConf(), jsonEventSerializer));
        cassandraACLMapper = new CassandraACLMapper(
            new CassandraACLMapper.StoreV2(usersRightDAO, aclDAOv2, eventStore),
            CassandraConfiguration.builder()
                .aclEnabled(Optional.of(false))
                .build());
    }

    @Test
    void getACLShouldReturnEmpty() {
        assertThat(cassandraACLMapper.getACL(MAILBOX_ID).block()).isEqualTo(MailboxACL.EMPTY);
    }

    @Test
    void getACLShouldNotIssueCassandraQueries(CassandraCluster cassandra) {
        StatementRecorder statementRecorder = cassandra.getConf().recordStatements();

        cassandraACLMapper.getACL(MAILBOX_ID).block();

        assertThat(statementRecorder.listExecutedStatements()).isEmpty();
    }

    @Test
    void deleteShouldBeSupported() {
        assertThatCode(() -> cassandraACLMapper.delete(MAILBOX_ID).block())
            .doesNotThrowAnyException();
    }

    @Test
    void setACLShouldNotBeSupported() {
        assertThatThrownBy(() -> cassandraACLMapper.setACL(MAILBOX_ID, MailboxACL.EMPTY).block())
            .isInstanceOf(NotImplementedException.class);
    }

    @Test
    void updateACLShouldNotBeSupported() {
        MailboxACL.EntryKey keyBenwa = new MailboxACL.EntryKey("benwa", MailboxACL.NameType.user, false);
        MailboxACL.Rfc4314Rights rights = new MailboxACL.Rfc4314Rights(MailboxACL.Right.Read);
        assertThatThrownBy(() -> cassandraACLMapper.updateACL(MAILBOX_ID, MailboxACL.command().key(keyBenwa).rights(rights).asAddition()).block())
            .isInstanceOf(NotImplementedException.class);
    }
}
