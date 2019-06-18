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

import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class ListMailboxAssertTest {
    private static final String OTHER_NAMESPACE = "other_namespace";
    private static final String NAME = "name";
    private static final String USER = "user";
    private static final String NAMESPACE = "namespace";
    private static final long UID_VALIDITY = 42;
    private static final Mailbox mailbox1 = new Mailbox(new MailboxPath(NAMESPACE, USER, NAME), UID_VALIDITY);
    private static final Mailbox mailbox2 = new Mailbox(new MailboxPath(OTHER_NAMESPACE, USER, NAME), UID_VALIDITY);

    private ListMailboxAssert listMaiboxAssert;
    private List<Mailbox> actualMailbox;

    @Before
    public void setUp() {
        actualMailbox = ImmutableList.of(mailbox1, mailbox2);
        listMaiboxAssert = ListMailboxAssert.assertMailboxes(actualMailbox);
    }

    @Test
    public void initListMailboxAssertShouldWork() {
        assertThat(listMaiboxAssert).isNotNull();
    }

    @Test
    public void assertListMailboxShouldWork() {
        assertMailboxes(actualMailbox).containOnly(new Mailbox(new MailboxPath(NAMESPACE, USER, NAME), UID_VALIDITY),
            new Mailbox(new MailboxPath(OTHER_NAMESPACE, USER, NAME), UID_VALIDITY));
    }
}
