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

import javax.inject.Inject;

import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionManager;
import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxACL;

import reactor.core.publisher.Mono;

public class CassandraACLMapper {
    public static final SchemaVersion ACL_V2_SCHEME_VERSION = new SchemaVersion(10);
    private final CassandraUserMailboxRightsDAO userMailboxRightsDAO;
    private final CassandraACLDAOV1 cassandraACLDAOV1;
    private final CassandraACLDAOV2 cassandraACLDAOV2;
    private final CassandraSchemaVersionManager versionManager;

    @Inject
    public CassandraACLMapper(CassandraUserMailboxRightsDAO userMailboxRightsDAO,
                              CassandraACLDAOV1 cassandraACLDAOV1,
                              CassandraACLDAOV2 cassandraACLDAOV2,
                              CassandraSchemaVersionManager versionManager) {
        this.cassandraACLDAOV1 = cassandraACLDAOV1;
        this.cassandraACLDAOV2 = cassandraACLDAOV2;
        this.userMailboxRightsDAO = userMailboxRightsDAO;
        this.versionManager = versionManager;
    }

    private Mono<CassandraACLDAO> aclDao() {
        return versionManager.isBefore(ACL_V2_SCHEME_VERSION)
            .map(isBefore -> {
                if (isBefore) {
                    return cassandraACLDAOV1;
                }
                return cassandraACLDAOV2;
            });
    }

    public Mono<MailboxACL> getACL(CassandraId cassandraId) {
        return aclDao().flatMap(dao -> dao.getACL(cassandraId));
    }

    public Mono<ACLDiff> updateACL(CassandraId cassandraId, MailboxACL.ACLCommand command) {
        return aclDao().flatMap(dao -> dao.updateACL(cassandraId, command)
            .flatMap(aclDiff -> userMailboxRightsDAO.update(cassandraId, aclDiff)
            .thenReturn(aclDiff))
            .switchIfEmpty(Mono.error(new MailboxException("Unable to update ACL"))));
    }

    public Mono<ACLDiff> setACL(CassandraId cassandraId, MailboxACL mailboxACL) {
        return aclDao().flatMap(dao -> dao.setACL(cassandraId, mailboxACL)
            .flatMap(aclDiff -> userMailboxRightsDAO.update(cassandraId, aclDiff)
            .thenReturn(aclDiff))
            .switchIfEmpty(Mono.defer(() -> Mono.error(new MailboxException("Unable to update ACL")))));
    }

    public Mono<Void> delete(CassandraId cassandraId) {
        return aclDao().flatMap(dao -> dao.delete(cassandraId));
    }
}
