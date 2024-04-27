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

package org.apache.james.mailbox.cassandra.mail.eventsourcing.acl;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.event.MailboxAggregateId;
import org.apache.james.event.acl.ACLUpdated;
import org.apache.james.eventsourcing.EventId;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.MailboxACL;
import org.junit.jupiter.api.Test;

class ACLUpdatedDTOTest {
    private static final String JSON = "{" +
        "  \"eventId\":0," +
        "  \"aggregateKey\":\"e22b3ac0-a80b-11e7-bb00-777268d65503\"," +
        "  \"type\":\"acl-updated\"," +
        "  \"aclDiff\":{" +
        "    \"oldAcl\":{\"entries\":{\"$any\":\"a\"}}," +
        "    \"newAcl\":{\"entries\":{\"$any\":\"l\"}}" +
        "  }" +
        "}";
    private static final MailboxACL.EntryKey ENTRY_KEY = MailboxACL.EntryKey.createGroupEntryKey("any", false);
    private static final MailboxACL.Rfc4314Rights RIGHTS = new MailboxACL.Rfc4314Rights(MailboxACL.Right.Administer);
    private static final ACLUpdated EVENT = aclUpdated();

    private static ACLUpdated aclUpdated() {
        try {
            MailboxACL oldACL = MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(RIGHTS)
                    .asAddition());
            MailboxACL newAcl = MailboxACL.EMPTY.apply(
                MailboxACL.command()
                    .key(ENTRY_KEY)
                    .rights(MailboxACL.Right.Lookup)
                    .asAddition());
            return new ACLUpdated(
                new MailboxAggregateId(CassandraId.of("e22b3ac0-a80b-11e7-bb00-777268d65503")),
                EventId.first(),
                ACLDiff.computeDiff(oldACL, newAcl));
        } catch (UnsupportedRightException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void shouldMatchSerializableContract() throws Exception {
        JsonSerializationVerifier.dtoModule(ACLModule.ACL_UPDATE)
            .bean(EVENT)
            .json(JSON)
            .verify();
    }
}