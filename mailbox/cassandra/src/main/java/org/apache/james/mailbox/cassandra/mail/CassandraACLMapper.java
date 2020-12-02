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

import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxACL;

import reactor.core.publisher.Mono;

public class CassandraACLMapper {
    private final CassandraUserMailboxRightsDAO userMailboxRightsDAO;
    private final CassandraACLDAO cassandraACLDAO;

    @Inject
    public CassandraACLMapper(CassandraUserMailboxRightsDAO userMailboxRightsDAO,
                              CassandraACLDAO cassandraACLDAO) {
        this.cassandraACLDAO = cassandraACLDAO;
        this.userMailboxRightsDAO = userMailboxRightsDAO;
    }

    public Mono<MailboxACL> getACL(CassandraId cassandraId) {
        return cassandraACLDAO.getACL(cassandraId);
    }

    public Mono<ACLDiff> updateACL(CassandraId cassandraId, MailboxACL.ACLCommand command) {
        return cassandraACLDAO.updateACL(cassandraId, command)
            .flatMap(aclDiff -> userMailboxRightsDAO.update(cassandraId, aclDiff)
            .thenReturn(aclDiff))
            .switchIfEmpty(Mono.error(new MailboxException("Unable to update ACL")));
    }

    public Mono<ACLDiff> setACL(CassandraId cassandraId, MailboxACL mailboxACL) {
        return cassandraACLDAO.setACL(cassandraId, mailboxACL)
            .flatMap(aclDiff -> userMailboxRightsDAO.update(cassandraId, aclDiff)
            .thenReturn(aclDiff))
            .switchIfEmpty(Mono.defer(() -> Mono.error(new MailboxException("Unable to update ACL"))));
    }

    public Mono<Void> delete(CassandraId cassandraId) {
        return cassandraACLDAO.delete(cassandraId);
    }
}
