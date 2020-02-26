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

package org.apache.james.mailbox.store.mail.model;

import static org.apache.james.mailbox.store.mail.model.ListMailboxAssert.assertMailboxes;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.james.core.Username;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.UidValidity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class ListMailboxAssertTest {
    static final String OTHER_NAMESPACE = "other_namespace";
    static final String NAME = "name";
    static final Username USER = Username.of("user");
    static final String NAMESPACE = "namespace";
    static final UidValidity UID_VALIDITY = UidValidity.of(42);
    static final MailboxId MAILBOX_ID_1 = TestId.of(1);
    static final MailboxId MAILBOX_ID_2 = TestId.of(2);
    static final Mailbox MAILBOX_1 = new Mailbox(new MailboxPath(NAMESPACE, USER, NAME), UID_VALIDITY, MAILBOX_ID_1);
    static final Mailbox MAILBOX_2 = new Mailbox(new MailboxPath(OTHER_NAMESPACE, USER, NAME), UID_VALIDITY, MAILBOX_ID_2);

    ListMailboxAssert listMaiboxAssert;
    List<Mailbox> actualMailboxes;

    @BeforeEach
    void setUp() {
        actualMailboxes = ImmutableList.of(MAILBOX_1, MAILBOX_2);
        listMaiboxAssert = ListMailboxAssert.assertMailboxes(actualMailboxes);
    }

    @Test
    void initListMailboxAssertShouldWork() {
        assertThat(listMaiboxAssert).isNotNull();
    }

    @Test
    void assertListMailboxShouldWork() {
        assertMailboxes(actualMailboxes).containOnly(new Mailbox(new MailboxPath(NAMESPACE, USER, NAME), UID_VALIDITY, MAILBOX_ID_1),
            new Mailbox(new MailboxPath(OTHER_NAMESPACE, USER, NAME), UID_VALIDITY, MAILBOX_ID_2));
    }
}
