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

package org.apache.james.mailbox.store;

import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StoreMailboxManagerTest {

    public static final int UID_VALIDITY = 42;
    private StoreMailboxManager<TestId> storeMailboxManager;

    @Before
    public void setUp() {
        storeMailboxManager = new StoreMailboxManager<TestId>(null, new MockAuthenticator(), new JVMMailboxPathLocker(), new UnionMailboxACLResolver(), new SimpleGroupMembershipResolver());
    }

    @Test
    public void belongsToNamespaceAndUserShouldReturnTrueWithIdenticalMailboxes() {
        MailboxPath path = new MailboxPath("namespace", "user", "name");
        assertThat(storeMailboxManager.belongsToNamespaceAndUser(path, new SimpleMailbox<TestId>(path, UID_VALIDITY))).isTrue();
    }

    @Test
    public void belongsToNamespaceAndUserShouldReturnTrueWithIdenticalMailboxesWithNullUser() {
        MailboxPath path = new MailboxPath("namespace", null, "name");
        assertThat(storeMailboxManager.belongsToNamespaceAndUser(path, new SimpleMailbox<TestId>(path, UID_VALIDITY))).isTrue();
    }

    @Test
    public void belongsToNamespaceAndUserShouldReturnTrueWithIdenticalMailboxesWithNullNamespace() {
        MailboxPath path = new MailboxPath(null, "user", "name");
        assertThat(storeMailboxManager.belongsToNamespaceAndUser(path,
            new SimpleMailbox<TestId>(new MailboxPath(null, "user", "name"), UID_VALIDITY))).isTrue();
    }

    @Test
    public void belongsToNamespaceAndUserShouldReturnTrueWithMailboxWithSameNamespaceAndUserWithNullUser() {
        MailboxPath path = new MailboxPath("namespace", null, "name");
        assertThat(storeMailboxManager.belongsToNamespaceAndUser(path,
            new SimpleMailbox<TestId>(new MailboxPath("namespace", null, "name2"), UID_VALIDITY))).isTrue();
    }

    @Test
    public void belongsToNamespaceAndUserShouldReturnTrueWithMailboxWithSameNamespaceAndUser() {
        MailboxPath path = new MailboxPath("namespace", "user", "name");
        assertThat(storeMailboxManager.belongsToNamespaceAndUser(path,
            new SimpleMailbox<TestId>(new MailboxPath("namespace", "user", "name2"), UID_VALIDITY))).isTrue();
    }

    @Test
    public void belongsToNamespaceAndUserShouldReturnFalseWithDifferentNamespace() {
        MailboxPath path = new MailboxPath("namespace", "user", "name");
        assertThat(storeMailboxManager.belongsToNamespaceAndUser(path,
            new SimpleMailbox<TestId>(new MailboxPath("namespace2", "user", "name"), UID_VALIDITY))).isFalse();
    }

    @Test
    public void belongsToNamespaceAndUserShouldReturnFalseWithDifferentUser() {
        MailboxPath path = new MailboxPath("namespace", "user", "name");
        assertThat(storeMailboxManager.belongsToNamespaceAndUser(path,
            new SimpleMailbox<TestId>(new MailboxPath("namespace", "user2", "name"), UID_VALIDITY))).isFalse();
    }
    @Test
    public void belongsToNamespaceAndUserShouldReturnFalseWithOneOfTheUserNull() {
        MailboxPath path = new MailboxPath("namespace", null, "name");
        assertThat(storeMailboxManager.belongsToNamespaceAndUser(path,
            new SimpleMailbox<TestId>(new MailboxPath("namespace", "user", "name"), UID_VALIDITY))).isFalse();
    }
    @Test
    public void belongsToNamespaceAndUserShouldReturnFalseIfNamespaceAreDifferentWithNullUser() {
        MailboxPath path = new MailboxPath("namespace", null, "name");
        assertThat(storeMailboxManager.belongsToNamespaceAndUser(path,
            new SimpleMailbox<TestId>(new MailboxPath("namespace2", null, "name"), UID_VALIDITY))).isFalse();
    }


}

