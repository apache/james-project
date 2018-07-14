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
package org.apache.james.mailbox.jpa;

import java.util.Optional;

import org.apache.james.backends.jpa.JpaTestCluster;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxManagerTest;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.openjpa.OpenJPAMailboxManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class JPAMailboxManagerTest extends MailboxManagerTest {

    private static final JpaTestCluster JPA_TEST_CLUSTER = JpaTestCluster.create(JPAMailboxFixture.MAILBOX_PERSISTANCE_CLASSES);
    private Optional<OpenJPAMailboxManager> openJPAMailboxManager = Optional.empty();

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
    }
    
    @Override
    protected MailboxManager provideMailboxManager() {
        if (!openJPAMailboxManager.isPresent()) {
            openJPAMailboxManager = Optional.of(JpaMailboxManagerProvider.provideMailboxManager(JPA_TEST_CLUSTER));
        }
        return openJPAMailboxManager.get();
    }

    @Override
    @After
    public void tearDown() throws MailboxException {
        JPA_TEST_CLUSTER.clear(JPAMailboxFixture.MAILBOX_TABLE_NAMES);
    }

    @Ignore("MAILBOX-343")
    @Test
    @Override
    public void creatingConcurrentlyMailboxesWithSameParentShouldNotFail() {

    }

}
