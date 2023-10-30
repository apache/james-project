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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.backends.jpa.JpaTestCluster;
import org.apache.james.mailbox.jpa.JPAId;
import org.apache.james.mailbox.jpa.JPAMailboxFixture;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.mailbox.store.mail.model.AnnotationMapperTest;
import org.junit.jupiter.api.AfterEach;

class JpaAnnotationMapperTest extends AnnotationMapperTest {

    static final JpaTestCluster JPA_TEST_CLUSTER = JpaTestCluster.create(JPAMailboxFixture.MAILBOX_PERSISTANCE_CLASSES);

    final AtomicInteger counter = new AtomicInteger();

    @AfterEach
    void tearDown() {
        JPA_TEST_CLUSTER.clear(JPAMailboxFixture.MAILBOX_TABLE_NAMES);
    }

    @Override
    protected AnnotationMapper createAnnotationMapper() {
        return new TransactionalAnnotationMapper(new JPAAnnotationMapper(JPA_TEST_CLUSTER.getEntityManagerFactory()));
    }

    @Override
    protected MailboxId generateMailboxId() {
        return JPAId.of(counter.incrementAndGet());
    }
}
