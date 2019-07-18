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
import static org.apache.james.vault.search.Operator.AFTER_OR_EQUALS;
import static org.apache.james.vault.search.Operator.BEFORE_OR_EQUALS;
import static org.apache.james.vault.search.Operator.CONTAINS;
import static org.apache.james.vault.search.Operator.CONTAINS_IGNORE_CASE;
import static org.apache.james.vault.search.Operator.EQUALS;
import static org.apache.james.vault.search.Operator.EQUALS_IGNORE_CASE;

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
            return builder.withMatcher(new Criterion.ValueMatcher<>(subString, CONTAINS, value -> value.contains(subString)));
        }

        public Criterion<String> containsIgnoreCase(String subString) {
            return builder.withMatcher(new Criterion.ValueMatcher<>(subString, CONTAINS_IGNORE_CASE, value -> value.toLowerCase(Locale.US).contains(subString.toLowerCase(Locale.US))));
        }

        public Criterion<String> equals(String expectedString) {
            return builder.withMatcher(new Criterion.ValueMatcher<>(expectedString, EQUALS, expectedString::equals));
        }

        public Criterion<String> equalsIgnoreCase(String expectedString) {
            return builder.withMatcher(new Criterion.ValueMatcher<>(expectedString, EQUALS_IGNORE_CASE, expectedString::equalsIgnoreCase));
        }
    }

    class ZonedDateTimeCriterionFactory {

        private final Criterion.ExpectMatcher<ZonedDateTime> builder;

        private ZonedDateTimeCriterionFactory(Criterion.ExpectMatcher<ZonedDateTime> builder) {
            this.builder = builder;
        }

        public Criterion<ZonedDateTime> beforeOrEquals(ZonedDateTime expectedValue) {
            return builder.withMatcher(new Criterion.ValueMatcher<>(expectedValue, BEFORE_OR_EQUALS, value -> !expectedValue.isBefore(value)));
        }

        public Criterion<ZonedDateTime> afterOrEquals(ZonedDateTime expectedValue) {
            return builder.withMatcher(new Criterion.ValueMatcher<>(expectedValue, AFTER_OR_EQUALS, value -> !expectedValue.isAfter(value)));
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
            .withMatcher(new Criterion.ValueMatcher<>(recipient, CONTAINS, value -> value.contains(recipient)));
    }

    static Criterion<MailAddress> hasSender(MailAddress sender) {
        return Criterion.Builder.forField(SENDER)
            .withMatcher(new Criterion.ValueMatcher<>(sender, EQUALS, sender::equals));
    }

    static Criterion<Boolean> hasAttachment() {
        return hasAttachment(true);
    }

    static Criterion<Boolean> hasNoAttachment() {
        return hasAttachment(false);
    }

    static Criterion<Boolean> hasAttachment(boolean hasAttachment) {
        return Criterion.Builder.forField(HAS_ATTACHMENT)
            .withMatcher(new Criterion.ValueMatcher<>(hasAttachment, EQUALS, value -> hasAttachment == value));
    }

    static StringCriterionFactory subject() {
        return new StringCriterionFactory(Criterion.Builder.forField(SUBJECT));
    }

    static Criterion<Collection<MailboxId>> containsOriginMailbox(MailboxId mailboxId) {
        return Criterion.Builder.forField(ORIGIN_MAILBOXES)
            .withMatcher(new Criterion.ValueMatcher<>(mailboxId, CONTAINS, value -> value.contains(mailboxId)));
    }
}
