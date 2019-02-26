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
import java.util.List;

import org.apache.james.core.MailAddress;
import org.apache.james.mailbox.model.MailboxId;

public interface CriterionFactory<T> {

    DeletedMessageField<T> deletedMessageField();

    interface EqualsMatcherFactory<T> extends CriterionFactory<T> {

        default Criterion<T> equalsMatcher(T testedValue) {
            ValueMatcher.Equals<T> matcher = () -> testedValue;
            return new Criterion<>(deletedMessageField(), matcher);
        }
    }

    interface StringMatcherFactory extends CriterionFactory<String> {

        default Criterion<String> contains(String subString) {
            ValueMatcher.StringContains matcher = () -> subString;
            return new Criterion<>(deletedMessageField(), matcher);
        }

        default Criterion<String> containsIgnoreCase(String subString) {
            ValueMatcher.StringContainsIgnoreCase matcher = () -> subString;
            return new Criterion<>(deletedMessageField(), matcher);
        }

        default Criterion<String> equals(String testedString) {
            ValueMatcher.Equals<String> matcher = () -> testedString;
            return new Criterion<>(deletedMessageField(), matcher);
        }
    }

    interface ZonedDateTimeMatcherFactory extends CriterionFactory<ZonedDateTime> {

        default Criterion<ZonedDateTime> beforeOrEquals(ZonedDateTime testedInstant) {
            ValueMatcher.ZonedDateTimeBeforeOrEquals matcher = () -> testedInstant;
            return new Criterion<>(deletedMessageField(), matcher);
        }

        default Criterion<ZonedDateTime> afterOrEquals(ZonedDateTime testedInstant) {
            ValueMatcher.ZonedDateTimeAfterOrEquals matcher = () -> testedInstant;
            return new Criterion<>(deletedMessageField(), matcher);
        }
    }

    interface ListMatcherFactory<T> extends CriterionFactory<List<T>> {

        default Criterion<List<T>> contains(T testedValue) {
            ValueMatcher.ListContains<T> matcher = () -> testedValue;
            return new Criterion<>(deletedMessageField(), matcher);
        }
    }

    static ZonedDateTimeMatcherFactory deletionDate() {
        return () -> DELETION_DATE;
    }

    static ZonedDateTimeMatcherFactory deliveryDate() {
        return () -> DELIVERY_DATE;
    }

    static ListMatcherFactory<MailAddress> recipients() {
        return () -> RECIPIENTS;
    }

    static EqualsMatcherFactory<MailAddress> sender() {
        return () -> SENDER;
    }

    static EqualsMatcherFactory<Boolean> hasAttachment() {
        return () -> HAS_ATTACHMENT;
    }

    static StringMatcherFactory subject() {
        return () -> SUBJECT;
    }

    static ListMatcherFactory<MailboxId> originMailboxes() {
        return () -> ORIGIN_MAILBOXES;
    }
}
