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

package org.apache.james.mailbox.jpa.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import javax.persistence.EntityManager;

import org.apache.james.backends.jpa.JpaTestCluster;
import org.apache.james.mailbox.jpa.JPAId;
import org.apache.james.mailbox.jpa.JPAMailboxFixture;
import org.apache.james.mailbox.jpa.mail.model.JPAMailbox;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.MailboxMapperTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class JpaMailboxMapperTest extends MailboxMapperTest {

    static final JpaTestCluster JPA_TEST_CLUSTER = JpaTestCluster.create(JPAMailboxFixture.MAILBOX_PERSISTANCE_CLASSES);

    final AtomicInteger counter = new AtomicInteger();

    @Override
    protected MailboxMapper createMailboxMapper() {
        return new TransactionalMailboxMapper(new JPAMailboxMapper(JPA_TEST_CLUSTER.getEntityManagerFactory()));
    }

    @Override
    protected MailboxId generateId() {
        return JPAId.of(counter.incrementAndGet());
    }

    @AfterEach
    void cleanUp() {
        JPA_TEST_CLUSTER.clear(JPAMailboxFixture.MAILBOX_TABLE_NAMES);
    }

    @Test
    void invalidUidValidityShouldBeSanitized() throws Exception {
        EntityManager entityManager = JPA_TEST_CLUSTER.getEntityManagerFactory().createEntityManager();

        entityManager.getTransaction().begin();
        JPAMailbox jpaMailbox = new JPAMailbox(benwaInboxPath, -1L);// set an invalid uid validity
        jpaMailbox.setUidValidity(-1L);
        entityManager.persist(jpaMailbox);
        entityManager.getTransaction().commit();

        Mailbox readMailbox = mailboxMapper.findMailboxByPath(benwaInboxPath).block();

        assertThat(readMailbox.getUidValidity().isValid()).isTrue();
    }

    @Test
    void uidValiditySanitizingShouldPersistTheSanitizedUidValidity() throws Exception {
        EntityManager entityManager = JPA_TEST_CLUSTER.getEntityManagerFactory().createEntityManager();

        entityManager.getTransaction().begin();
        JPAMailbox jpaMailbox = new JPAMailbox(benwaInboxPath, -1L);// set an invalid uid validity
        jpaMailbox.setUidValidity(-1L);
        entityManager.persist(jpaMailbox);
        entityManager.getTransaction().commit();

        Mailbox readMailbox1 = mailboxMapper.findMailboxByPath(benwaInboxPath).block();
        Mailbox readMailbox2 = mailboxMapper.findMailboxByPath(benwaInboxPath).block();

        assertThat(readMailbox1.getUidValidity()).isEqualTo(readMailbox2.getUidValidity());
    }
}
