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

package org.apache.james.mailbox.cassandra;

import java.util.Set;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.StatementRecorder;
import org.apache.james.mailbox.cassandra.mail.MailboxAggregateModule;
import org.apache.james.mailbox.events.EventBus;
import org.apache.james.mailbox.extension.PreDeletionHook;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.store.AbstractMessageIdManagerSideEffectTest;
import org.apache.james.mailbox.store.MessageIdManagerTestSystem;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;

class CassandraMessageIdManagerSideEffectTest extends AbstractMessageIdManagerSideEffectTest {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(MailboxAggregateModule.MODULE);

    @Override
    protected MessageIdManagerTestSystem createTestSystem(QuotaManager quotaManager, EventBus eventBus, Set<PreDeletionHook> preDeletionHooks) {
        return CassandraMessageIdManagerTestSystem.createTestingData(cassandraCluster.getCassandraCluster(), quotaManager, eventBus, preDeletionHooks);
    }

    @Disabled("11 mailbox reads and 10 acl reads")
    @Test
    void setInMailboxesShouldLimitMailboxReads(CassandraCluster cassandra) throws Exception {
        givenUnlimitedQuota();
        MessageId messageId = testingData.persist(mailbox2.getMailboxId(), messageUid1, FLAGS, session);

        StatementRecorder statementRecorder = new StatementRecorder();
        cassandra.getConf().recordStatements(statementRecorder);

        messageIdManager.setInMailboxes(messageId, ImmutableList.of(mailbox1.getMailboxId()), session);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(statementRecorder.listExecutedStatements(
                StatementRecorder.Selector.preparedStatement("SELECT id,mailboxbase,uidvalidity,name FROM mailbox WHERE id=:id;")))
                .hasSize(3); // an extra read is still performed
            softly.assertThat(statementRecorder.listExecutedStatements(
                StatementRecorder.Selector.preparedStatement("SELECT acl,version FROM acl WHERE id=:id;")))
                .hasSize(2);
        });
    }
}
