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

package org.apache.james.mailbox.events;

import java.util.Objects;

import jakarta.inject.Inject;

import org.apache.james.events.RegistrationKey;
import org.apache.james.mailbox.model.MailboxId;

public class MailboxIdRegistrationKey implements RegistrationKey {
    public static class Factory implements RegistrationKey.Factory {
        private final MailboxId.Factory mailboxIdFactory;

        @Inject
        public Factory(MailboxId.Factory mailboxIdFactory) {
            this.mailboxIdFactory = mailboxIdFactory;
        }

        @Override
        public Class<? extends RegistrationKey> forClass() {
            return MailboxIdRegistrationKey.class;
        }

        @Override
        public RegistrationKey fromString(String asString) {
            return new MailboxIdRegistrationKey(mailboxIdFactory.fromString(asString));
        }
    }

    private final MailboxId mailboxId;

    public MailboxIdRegistrationKey(MailboxId mailboxId) {
        this.mailboxId = mailboxId;
    }

    public MailboxId getMailboxId() {
        return mailboxId;
    }

    @Override
    public String asString() {
        return mailboxId.serialize();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MailboxIdRegistrationKey) {
            MailboxIdRegistrationKey that = (MailboxIdRegistrationKey) o;

            return Objects.equals(this.mailboxId, that.mailboxId);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(mailboxId);
    }
}
