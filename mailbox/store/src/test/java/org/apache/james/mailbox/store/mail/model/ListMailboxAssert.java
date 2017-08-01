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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.james.mailbox.model.MailboxId;

import com.google.common.base.Objects;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;

public class ListMailboxAssert {

    private final List<Mailbox> actual;

    private final List<InnerMailbox> mailboxtoInnerMailbox(List<Mailbox> mailboxes) {
        return FluentIterable.from(mailboxes)
            .transform(mailbox ->
                new InnerMailbox(mailbox.getMailboxId(), mailbox.getUser(), mailbox.getName(), mailbox.getNamespace()))
            .toList();
    }

    private ListMailboxAssert(List<Mailbox> actual) {
        this.actual = actual;
    }

    public static ListMailboxAssert assertMailboxes(List<Mailbox> actual) {
        return new ListMailboxAssert(actual);
    }

    public void containOnly(Mailbox... expecteds) {
        assertThat(mailboxtoInnerMailbox(actual)).containsOnlyElementsOf(mailboxtoInnerMailbox(Lists.newArrayList(expecteds)));
    }

    private final class InnerMailbox {
        private final MailboxId id;
        private final String user;
        private final String name;
        private final String namespace;

        public InnerMailbox(MailboxId id, String user, String name, String namespace) {
            this.id = id;
            this.user = user;
            this.name = name;
            this.namespace = namespace;
        }

        public MailboxId getId() {
            return id;
        }

        public String getUser() {
            return user;
        }

        public String getName() {
            return name;
        }

        public String getNamespace() {
            return namespace;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id, user, name, namespace);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof InnerMailbox) {
                InnerMailbox o = (InnerMailbox)obj;
                return Objects.equal(id, o.getId()) 
                    && Objects.equal(name, o.getName()) 
                    && Objects.equal(namespace, o.getNamespace()) 
                    && Objects.equal(user, o.getUser());
            }
            return false;
        }
        
    }
}
