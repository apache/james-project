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

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class ListMailboxAssertTest {
    private static final String OTHER_NAMESPACE = "other_namespace";
    private static final String NAME = "name";
    private static final String USER = "user";
    private static final String NAMESPACE = "namespace";
    private static final long UID_VALIDITY = 42;
    private static final Mailbox mailbox1 = new SimpleMailbox(new MailboxPath(NAMESPACE, USER, NAME), UID_VALIDITY);
    private static final Mailbox mailbox2 = new SimpleMailbox(new MailboxPath(OTHER_NAMESPACE, USER, NAME), UID_VALIDITY);

    private ListMailboxAssert listMaiboxAssert;
    private List<Mailbox> actualMailbox;

    @Before
    public void setUp() {
        actualMailbox = ImmutableList.of(mailbox1, mailbox2);
        listMaiboxAssert = ListMailboxAssert.assertMailboxes(actualMailbox);
    }

    @Test
    public void initListMailboxAssertShouldWork() throws Exception {
        assertThat(listMaiboxAssert).isNotNull();
    }

    @Test
    public void assertListMailboxShouldWork() throws Exception {
        assertMailboxes(actualMailbox).containOnly(createMailbox(NAMESPACE, USER, NAME, UID_VALIDITY), 
            createMailbox(OTHER_NAMESPACE, USER, NAME, UID_VALIDITY));
    }
    
    private Mailbox createMailbox(final String namespace, final String user, final String name, final long uid_validity) {
        return new Mailbox() {

            @Override
            public MailboxPath generateAssociatedPath() {
                return new MailboxPath(getNamespace(), getUser(), getName());
            }

            @Override
            public void setUser(String user) {
            }
            
            @Override
            public void setNamespace(String namespace) {
            }
            
            @Override
            public void setName(String name) {
            }
            
            @Override
            public void setMailboxId(MailboxId id) {
            }
            
            @Override
            public void setACL(MailboxACL acl) {
            }
            
            @Override
            public String getUser() {
                return user;
            }
            
            @Override
            public long getUidValidity() {
                return uid_validity;
            }
            
            @Override
            public String getNamespace() {
                return namespace;
            }
            
            @Override
            public String getName() {
                return name;
            }
            
            @Override
            public MailboxId getMailboxId() {
                return null;
            }
            
            @Override
            public MailboxACL getACL() {
                return null;
            }

            @Override
            public boolean isChildOf(Mailbox potentialParent, MailboxSession mailboxSession) {
                throw new NotImplementedException("Not implemented");
            }
        };
    }
}
