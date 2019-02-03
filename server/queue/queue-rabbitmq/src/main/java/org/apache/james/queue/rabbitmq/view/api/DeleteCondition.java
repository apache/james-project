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

package org.apache.james.queue.rabbitmq.view.api;

import java.util.Objects;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.queue.api.ManageableMailQueue;
import org.apache.mailet.Mail;

import com.google.common.base.Preconditions;

public interface DeleteCondition {
    boolean shouldBeDeleted(Mail mail);

    class All implements DeleteCondition {
        @Override
        public boolean shouldBeDeleted(Mail mail) {
            Preconditions.checkNotNull(mail);
            return true;
        }

        @Override
        public final boolean equals(Object obj) {
            return obj instanceof All;
        }

        @Override
        public final int hashCode() {
            return Objects.hashCode(All.class);
        }
    }

    class WithSender implements DeleteCondition {
        private final String senderAsString;

        private WithSender(String senderAsString) {
            this.senderAsString = senderAsString;
        }

        @Override
        public boolean shouldBeDeleted(Mail mail) {
            Preconditions.checkNotNull(mail);
            return mail.getMaybeSender()
                .asString()
                .equals(senderAsString);
        }

        @Override
        public final boolean equals(Object obj) {
            if (obj instanceof WithSender) {
                WithSender that = (WithSender) obj;

                return Objects.equals(this.senderAsString, that.senderAsString);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hashCode(senderAsString);
        }
    }

    class WithName implements DeleteCondition {
        private final String name;

        private WithName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean shouldBeDeleted(Mail mail) {
            Preconditions.checkNotNull(mail);
            return mail.getName().equals(name);
        }

        @Override
        public final boolean equals(Object obj) {
            if (obj instanceof WithName) {
                WithName that = (WithName) obj;

                return Objects.equals(this.name, that.name);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hashCode(name);
        }
    }

    class WithRecipient implements DeleteCondition {
        private final String recipientAsString;

        private WithRecipient(String recipientAsString) {
            this.recipientAsString = recipientAsString;
        }

        @Override
        public boolean shouldBeDeleted(Mail mail) {
            Preconditions.checkNotNull(mail);
            return mail.getRecipients()
                .stream()
                .anyMatch(mailAddress -> mailAddress.asString().equals(recipientAsString));
        }

        @Override
        public final boolean equals(Object obj) {
            if (obj instanceof WithRecipient) {
                WithRecipient that = (WithRecipient) obj;

                return Objects.equals(this.recipientAsString, that.recipientAsString);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hashCode(recipientAsString);
        }
    }

    static DeleteCondition from(ManageableMailQueue.Type deletionType, String value) {
        switch (deletionType) {
            case Name:
                return withName(value);
            case Sender:
                return withSender(value);
            case Recipient:
                return withRecipient(value);
            default:
                throw new NotImplementedException(deletionType + " is not handled");
        }
    }

    static WithRecipient withRecipient(String value) {
        Preconditions.checkNotNull(value);
        return new WithRecipient(value);
    }

    static WithSender withSender(String value) {
        Preconditions.checkNotNull(value);
        return new WithSender(value);
    }

    static WithName withName(String value) {
        Preconditions.checkNotNull(value);
        return new WithName(value);
    }

    static DeleteCondition all() {
        return new All();
    }
}
