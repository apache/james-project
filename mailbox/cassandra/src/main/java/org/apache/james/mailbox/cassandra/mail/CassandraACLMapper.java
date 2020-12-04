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

import java.util.Set;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.eventsourcing.Command;
import org.apache.james.eventsourcing.CommandHandler;
import org.apache.james.eventsourcing.EventSourcingSystem;
import org.apache.james.eventsourcing.Subscriber;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.mail.eventsourcing.acl.ACLReseted;
import org.apache.james.mailbox.cassandra.mail.eventsourcing.acl.ACLUpdated;
import org.apache.james.mailbox.cassandra.mail.eventsourcing.acl.AclV2DAOSubscriber;
import org.apache.james.mailbox.cassandra.mail.eventsourcing.acl.DeleteMailboxCommand;
import org.apache.james.mailbox.cassandra.mail.eventsourcing.acl.MailboxAggregateId;
import org.apache.james.mailbox.cassandra.mail.eventsourcing.acl.SetACLCommand;
import org.apache.james.mailbox.cassandra.mail.eventsourcing.acl.UpdateACLCommand;
import org.apache.james.mailbox.cassandra.mail.eventsourcing.acl.UserRightsDAOSubscriber;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxACL;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

public class CassandraACLMapper {
    public interface Store {
        Mono<MailboxACL> getACL(CassandraId cassandraId);

        Mono<ACLDiff> updateACL(CassandraId cassandraId, MailboxACL.ACLCommand command);

        Mono<ACLDiff> setACL(CassandraId cassandraId, MailboxACL mailboxACL);

        Mono<Void> delete(CassandraId cassandraId);
    }

    public static class StoreV1 implements Store {
        private final CassandraUserMailboxRightsDAO userMailboxRightsDAO;
        private final CassandraACLDAOV1 cassandraACLDAOV1;

        @Inject
        public StoreV1(CassandraUserMailboxRightsDAO userMailboxRightsDAO, CassandraACLDAOV1 cassandraACLDAOV1) {
            this.userMailboxRightsDAO = userMailboxRightsDAO;
            this.cassandraACLDAOV1 = cassandraACLDAOV1;
        }

        @Override
        public Mono<MailboxACL> getACL(CassandraId cassandraId) {
            return cassandraACLDAOV1.getACL(cassandraId);
        }

        @Override
        public Mono<ACLDiff> updateACL(CassandraId cassandraId, MailboxACL.ACLCommand command) {
            return cassandraACLDAOV1.updateACL(cassandraId, command)
                .flatMap(aclDiff -> userMailboxRightsDAO.update(cassandraId, aclDiff)
                    .thenReturn(aclDiff))
                .switchIfEmpty(Mono.error(new MailboxException("Unable to update ACL")));
        }

        @Override
        public Mono<ACLDiff> setACL(CassandraId cassandraId, MailboxACL mailboxACL) {
            return cassandraACLDAOV1.setACL(cassandraId, mailboxACL)
                .flatMap(aclDiff -> userMailboxRightsDAO.update(cassandraId, aclDiff)
                    .thenReturn(aclDiff))
                .switchIfEmpty(Mono.defer(() -> Mono.error(new MailboxException("Unable to update ACL"))));
        }

        public Mono<Void> delete(CassandraId cassandraId) {
            return cassandraACLDAOV1.getACL(cassandraId)
                .flatMap(acl -> userMailboxRightsDAO.update(cassandraId, ACLDiff.computeDiff(acl, MailboxACL.EMPTY))
                    .then(cassandraACLDAOV1.delete(cassandraId)));
        }
    }

    public static class StoreV2 implements Store {
        private final CassandraACLDAOV2 cassandraACLDAOV2;
        private final EventSourcingSystem eventSourcingSystem;

        @Inject
        public StoreV2(CassandraUserMailboxRightsDAO userMailboxRightsDAO,
                CassandraACLDAOV2 cassandraACLDAOV2,
                EventStore eventStore) {
            this.cassandraACLDAOV2 = cassandraACLDAOV2;
            Set<CommandHandler<? extends Command>> commandHandlers = ImmutableSet.of(new DeleteMailboxCommand.CommandHandler(eventStore),
                new UpdateACLCommand.CommandHandler(eventStore),
                new SetACLCommand.CommandHandler(eventStore));
            Set<Subscriber> subscribers = ImmutableSet.of(new UserRightsDAOSubscriber(userMailboxRightsDAO),
                new AclV2DAOSubscriber(cassandraACLDAOV2));
            eventSourcingSystem = EventSourcingSystem.fromJava(commandHandlers, subscribers, eventStore);
        }

        @Override
        public Mono<MailboxACL> getACL(CassandraId cassandraId) {
            return cassandraACLDAOV2.getACL(cassandraId);
        }

        @Override
        public Mono<ACLDiff> updateACL(CassandraId cassandraId, MailboxACL.ACLCommand command) {
            return Mono.from(eventSourcingSystem.dispatch(new UpdateACLCommand(new MailboxAggregateId(cassandraId), command)))
                .flatMapIterable(events -> events)
                .filter(ACLUpdated.class::isInstance)
                .map(ACLUpdated.class::cast)
                .map(ACLUpdated::getAclDiff)
                .next()
                .switchIfEmpty(Mono.defer(() -> Mono.error(new MailboxException("Unable to update ACL"))));
        }

        @Override
        public Mono<ACLDiff> setACL(CassandraId cassandraId, MailboxACL mailboxACL) {
            return Mono.from(eventSourcingSystem.dispatch(new SetACLCommand(new MailboxAggregateId(cassandraId), mailboxACL)))
                .flatMapIterable(events -> events)
                .filter(ACLReseted.class::isInstance)
                .map(ACLReseted.class::cast)
                .map(ACLReseted::getAclDiff)
                .next()
                .switchIfEmpty(Mono.defer(() -> Mono.error(new MailboxException("Unable to set ACL"))));
        }

        @Override
        public Mono<Void> delete(CassandraId cassandraId) {
            return Mono.from(eventSourcingSystem.dispatch(new DeleteMailboxCommand(new MailboxAggregateId(cassandraId)))).then();
        }
    }

    public static final SchemaVersion ACL_V2_SCHEME_VERSION = new SchemaVersion(10);
    private final StoreV1 storeV1;
    private final StoreV2 storeV2;
    private final CassandraSchemaVersionManager versionManager;

    @Inject
    public CassandraACLMapper(StoreV1 storeV1, StoreV2 storeV2, CassandraSchemaVersionManager versionManager) {
        this.storeV1 = storeV1;
        this.storeV2 = storeV2;

        this.versionManager = versionManager;
    }

    private Mono<Store> store() {
        return versionManager.isBefore(ACL_V2_SCHEME_VERSION)
            .map(isBefore -> {
                if (isBefore) {
                    return storeV1;
                }
                return storeV2;
            });
    }

    public Mono<MailboxACL> getACL(CassandraId cassandraId) {
        return store().flatMap(store -> store.getACL(cassandraId));
    }

    public Mono<ACLDiff> updateACL(CassandraId cassandraId, MailboxACL.ACLCommand command) {
        return store().flatMap(store -> store.updateACL(cassandraId, command));
    }

    public Mono<ACLDiff> setACL(CassandraId cassandraId, MailboxACL mailboxACL) {
        return store().flatMap(store -> store.setACL(cassandraId, mailboxACL));
    }

    public Mono<Void> delete(CassandraId cassandraId) {
        return store().flatMap(store -> store.delete(cassandraId));
    }
}
