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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.init.CassandraModuleComposite;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.cassandra.modules.CassandraAclModule;
import org.apache.james.mailbox.cassandra.modules.CassandraMailboxModule;
import org.apache.james.mailbox.cassandra.modules.CassandraUidAndModSeqModule;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.github.fge.lambdas.Throwing;

/**
 * Unit tests for UidProvider and ModSeqProvider.
 * 
 */
public class CassandraUidAndModSeqProviderTest {

    private static final CassandraCluster CASSANDRA = CassandraCluster.create(new CassandraModuleComposite(
        new CassandraAclModule(),
        new CassandraMailboxModule(),
        new CassandraUidAndModSeqModule()));
    private static final int NAMESPACES = 5;
    private static final int USERS = 5;
    private static final int MAILBOX_NO = 5;
    private static final int MAX_RETRY = 100;
    private static final char SEPARATOR = '%';
    
    private CassandraUidProvider uidProvider;
    private CassandraModSeqProvider modSeqProvider;
    private CassandraMailboxMapper mapper;
    private List<SimpleMailbox<CassandraId>> mailboxList;
    private List<MailboxPath> pathsList;

    @Before
    public void setUpClass() throws Exception {
        CASSANDRA.ensureAllTables();
        uidProvider = new CassandraUidProvider(CASSANDRA.getConf());
        modSeqProvider = new CassandraModSeqProvider(CASSANDRA.getConf());
        mapper = new CassandraMailboxMapper(CASSANDRA.getConf(), CASSANDRA.getTypesProvider(), MAX_RETRY);
        fillMailboxList();
        for (SimpleMailbox<CassandraId> mailbox : mailboxList) {
            mapper.save(mailbox);
        }
    }
    
    @After
    public void cleanUp() {
        CASSANDRA.clearAllTables();
    }

    private void fillMailboxList() {
        mailboxList = new ArrayList<>();
        pathsList = new ArrayList<>();
        MailboxPath path;
        String name;
        for (int i = 0; i < NAMESPACES; i++) {
            for (int j = 0; j < USERS; j++) {
                for (int k = 0; k < MAILBOX_NO; k++) {
                    if (j == 3) {
                        name = "test" + SEPARATOR + "subbox" + k;
                    } else {
                        name = "mailbox" + k;
                    }
                    path = new MailboxPath("namespace" + i, "user" + j, name);
                    pathsList.add(path);
                    mailboxList.add(new SimpleMailbox<>(path, 13));
                }
            }
        }
    }

    @Test
    public void lastUidShouldRetrieveValueStoredByNextUid() throws Exception {
        MailboxPath path = new MailboxPath("gsoc", "ieugen", "Trash");
        SimpleMailbox<CassandraId> newBox = new SimpleMailbox<>(path, 1234);
        mapper.save(newBox);
        mailboxList.add(newBox);
        pathsList.add(path);

        long result = uidProvider.lastUid(null, newBox);
        assertEquals(0, result);
        LongStream.range(1, 10)
            .forEach(Throwing.longConsumer(value -> {
                        long uid = uidProvider.nextUid(null, newBox);
                        assertThat(uid).isEqualTo(uidProvider.lastUid(null, newBox));
                })
            );
    }

    @Test
    public void nextUidShouldIncrementValueByOne() throws Exception {
        SimpleMailbox<CassandraId> mailbox = mailboxList.get(mailboxList.size() / 2);
        long lastUid = uidProvider.lastUid(null, mailbox);
        LongStream.range(lastUid + 1, lastUid + 10)
            .forEach(Throwing.longConsumer(value -> {
                        long result = uidProvider.nextUid(null, mailbox);
                        assertThat(value).isEqualTo(result);
                })
            );
    }

    @Test
    public void highestModSeqShouldRetrieveValueStoredNextModSeq() throws Exception {
        MailboxPath path = new MailboxPath("gsoc", "ieugen", "Trash");
        SimpleMailbox<CassandraId> newBox = new SimpleMailbox<>(path, 1234);
        mapper.save(newBox);
        mailboxList.add(newBox);
        pathsList.add(path);

        long result = modSeqProvider.highestModSeq(null, newBox);
        assertEquals(0, result);
        LongStream.range(1, 10)
            .forEach(Throwing.longConsumer(value -> {
                        long uid = modSeqProvider.nextModSeq(null, newBox);
                        assertThat(uid).isEqualTo(modSeqProvider.highestModSeq(null, newBox));
                })
            );
    }

    @Test
    public void nextModSeqShouldIncrementValueByOne() throws Exception {
        SimpleMailbox<CassandraId> mailbox = mailboxList.get(mailboxList.size() / 2);
        long lastUid = modSeqProvider.highestModSeq(null, mailbox);
        LongStream.range(lastUid + 1, lastUid + 10)
            .forEach(Throwing.longConsumer(value -> {
                        long result = modSeqProvider.nextModSeq(null, mailbox);
                        assertThat(value).isEqualTo(result);
                })
            );
    }

    @Test
    public void nextModSeqShouldGenerateUniqueValuesWhenParallelCalls() throws Exception {
        SimpleMailbox<CassandraId> mailbox = mailboxList.get(mailboxList.size() / 2);
        long lastModSeq = modSeqProvider.highestModSeq(null, mailbox);
        int nbEntries = 1000;
        long nbValues = LongStream.range(lastModSeq, lastModSeq + nbEntries)
            .parallel()
            .map(Throwing.longUnaryOperator(x -> modSeqProvider.nextModSeq(null, mailbox)))
            .distinct()
            .count();
        assertThat(nbValues).isEqualTo(nbEntries);
        
    }
    
}
