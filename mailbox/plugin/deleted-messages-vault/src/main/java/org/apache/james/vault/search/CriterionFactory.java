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

package org.apache.james.vault.search;

import static org.apache.james.vault.search.DeletedMessageField.DELETION_DATE;
import static org.apache.james.vault.search.DeletedMessageField.DELIVERY_DATE;
import static org.apache.james.vault.search.DeletedMessageField.HAS_ATTACHMENT;
import static org.apache.james.vault.search.DeletedMessageField.ORIGIN_MAILBOXES;
import static org.apache.james.vault.search.DeletedMessageField.RECIPIENTS;
import static org.apache.james.vault.search.DeletedMessageField.SENDER;
import static org.apache.james.vault.search.DeletedMessageField.SUBJECT;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Locale;

import org.apache.james.core.MailAddress;
import org.apache.james.mailbox.model.MailboxId;

public interface CriterionFactory {

    class StringCriterionFactory {

        private final Criterion.ExpectMatcher<String> builder;

        private StringCriterionFactory(Criterion.ExpectMatcher<String> builder) {
            this.builder = builder;
        }

        public Criterion<String> contains(String subString) {
            return builder.withMatcher(value -> value.contains(subString));
        }

        public Criterion<String> containsIgnoreCase(String subString) {
            return builder.withMatcher(value -> value.toLowerCase(Locale.US).contains(subString.toLowerCase(Locale.US)));
        }

        public Criterion<String> equals(String expectedString) {
            return builder.withMatcher(expectedString::equals);
        }

        public Criterion<String> equalsIgnoreCase(String expectedString) {
            return builder.withMatcher(expectedString::equalsIgnoreCase);
        }
    }

    class ZonedDateTimeCriterionFactory {

        private final Criterion.ExpectMatcher<ZonedDateTime> builder;

        private ZonedDateTimeCriterionFactory(Criterion.ExpectMatcher<ZonedDateTime> builder) {
            this.builder = builder;
        }

        public Criterion<ZonedDateTime> beforeOrEquals(ZonedDateTime expectedValue) {
            return builder.withMatcher(actualValue -> !expectedValue.isBefore(actualValue));
        }

        public Criterion<ZonedDateTime> afterOrEquals(ZonedDateTime expectedValue) {
            return builder.withMatcher(actualValue -> !expectedValue.isAfter(actualValue));
        }
    }

    static ZonedDateTimeCriterionFactory deletionDate() {
        return new ZonedDateTimeCriterionFactory(Criterion.Builder.forField(DELETION_DATE));
    }

    static ZonedDateTimeCriterionFactory deliveryDate() {
        return new ZonedDateTimeCriterionFactory(Criterion.Builder.forField(DELIVERY_DATE));
    }

    static Criterion<Collection<MailAddress>> containsRecipient(MailAddress recipient) {
        return Criterion.Builder.forField(RECIPIENTS)
            .withMatcher(actualValue -> actualValue.contains(recipient));
    }

    static Criterion<MailAddress> hasSender(MailAddress sender) {
        return Criterion.Builder.forField(SENDER)
            .withMatcher(sender::equals);
    }

    static Criterion<Boolean> hasAttachment() {
        return hasAttachment(true);
    }

    static Criterion<Boolean> hasNoAttachment() {
        return hasAttachment(false);
    }

    static Criterion<Boolean> hasAttachment(boolean hasAttachment) {
        return Criterion.Builder.forField(HAS_ATTACHMENT)
            .withMatcher(actualValue -> hasAttachment == actualValue);
    }

    static StringCriterionFactory subject() {
        return new StringCriterionFactory(Criterion.Builder.forField(SUBJECT));
    }

    static Criterion<Collection<MailboxId>> containsOriginMailbox(MailboxId mailboxId) {
        return Criterion.Builder.forField(ORIGIN_MAILBOXES)
            .withMatcher(actualValue -> actualValue.contains(mailboxId));
    }
}
