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

package org.apache.james.jmap.api.vacation;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.util.ValuePatch;

import com.google.common.base.Preconditions;

public class VacationPatch {

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builderFrom(Vacation vacation) {
        return VacationPatch.builder()
            .subject(ValuePatch.ofOptional(vacation.getSubject()))
            .textBody(ValuePatch.ofOptional(vacation.getTextBody()))
            .htmlBody(ValuePatch.ofOptional(vacation.getHtmlBody()))
            .fromDate(ValuePatch.ofOptional(vacation.getFromDate()))
            .toDate(ValuePatch.ofOptional(vacation.getToDate()))
            .isEnabled(ValuePatch.modifyTo(vacation.isEnabled()));
    }

    public static class Builder {
        private ValuePatch<String> subject = ValuePatch.keep();
        private ValuePatch<String> textBody = ValuePatch.keep();
        private ValuePatch<String> htmlBody = ValuePatch.keep();
        private ValuePatch<ZonedDateTime> toDate = ValuePatch.keep();
        private ValuePatch<ZonedDateTime> fromDate = ValuePatch.keep();
        private ValuePatch<Boolean> isEnabled = ValuePatch.keep();

        public Builder subject(ValuePatch<String> subject) {
            Preconditions.checkNotNull(subject);
            this.subject = subject;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = ValuePatch.modifyTo(subject);
            return this;
        }

        public Builder textBody(ValuePatch<String> textBody) {
            Preconditions.checkNotNull(textBody);
            this.textBody = textBody;
            return this;
        }

        public Builder textBody(String textBody) {
            this.textBody = ValuePatch.modifyTo(textBody);
            return this;
        }

        public Builder htmlBody(ValuePatch<String> htmlBody) {
            Preconditions.checkNotNull(htmlBody);
            this.htmlBody = htmlBody;
            return this;
        }

        public Builder htmlBody(String htmlBody) {
            this.htmlBody = ValuePatch.modifyTo(htmlBody);
            return this;
        }

        public Builder toDate(ValuePatch<ZonedDateTime> toDate) {
            Preconditions.checkNotNull(toDate);
            this.toDate = toDate;
            return this;
        }

        public Builder toDate(ZonedDateTime toDate) {
            this.toDate = ValuePatch.modifyTo(toDate);
            return this;
        }

        public Builder fromDate(ValuePatch<ZonedDateTime> fromDate) {
            Preconditions.checkNotNull(fromDate);
            this.fromDate = fromDate;
            return this;
        }

        public Builder fromDate(ZonedDateTime fromDate) {
            this.fromDate = ValuePatch.modifyTo(fromDate);
            return this;
        }

        public Builder isEnabled(ValuePatch<Boolean> isEnabled) {
            Preconditions.checkNotNull(isEnabled);
            this.isEnabled = isEnabled;
            return this;
        }

        public Builder isEnabled(Boolean isEnabled) {
            this.isEnabled = ValuePatch.modifyTo(isEnabled);
            return this;
        }

        public Builder addAll(Builder other) {
            return builder()
                .isEnabled(this.isEnabled.merge(other.isEnabled))
                .fromDate(this.fromDate.merge(other.fromDate))
                .toDate(this.toDate.merge(other.toDate))
                .subject(this.subject.merge(other.subject))
                .htmlBody(this.htmlBody.merge(other.htmlBody))
                .textBody(this.textBody.merge(other.textBody));
        }

        public VacationPatch build() {
            return new VacationPatch(subject, textBody, htmlBody, toDate, fromDate, isEnabled);
        }
    }

    private final ValuePatch<String> subject;
    private final ValuePatch<String> textBody;
    private final ValuePatch<String> htmlBody;
    private final ValuePatch<ZonedDateTime> toDate;
    private final ValuePatch<ZonedDateTime> fromDate;
    private final ValuePatch<Boolean> isEnabled;

    private VacationPatch(ValuePatch<String> subject, ValuePatch<String> textBody, ValuePatch<String> htmlBody,
                          ValuePatch<ZonedDateTime> toDate, ValuePatch<ZonedDateTime> fromDate, ValuePatch<Boolean> isEnabled) {
        Preconditions.checkState(!isEnabled.isRemoved());
        this.subject = subject;
        this.textBody = textBody;
        this.htmlBody = htmlBody;
        this.toDate = toDate;
        this.fromDate = fromDate;
        this.isEnabled = isEnabled;
    }

    public ValuePatch<String> getSubject() {
        return subject;
    }

    public ValuePatch<String> getTextBody() {
        return textBody;
    }

    public ValuePatch<String> getHtmlBody() {
        return htmlBody;
    }

    public ValuePatch<ZonedDateTime> getToDate() {
        return toDate;
    }

    public ValuePatch<ZonedDateTime> getFromDate() {
        return fromDate;
    }

    public ValuePatch<Boolean> getIsEnabled() {
        return isEnabled;
    }

    public Vacation patch(Vacation vacation) {
        return Vacation.builder()
            .subject(subject.notKeptOrElse(vacation.getSubject()))
            .fromDate(fromDate.notKeptOrElse(vacation.getFromDate()))
            .toDate(toDate.notKeptOrElse(vacation.getToDate()))
            .textBody(textBody.notKeptOrElse(vacation.getTextBody()))
            .htmlBody(htmlBody.notKeptOrElse(vacation.getHtmlBody()))
            .enabled(isEnabled.notKeptOrElse(Optional.of(vacation.isEnabled())).get())
            .build();
    }

    public boolean isIdentity() {
        return subject.isKept()
            && textBody.isKept()
            && htmlBody.isKept()
            && toDate.isKept()
            && fromDate.isKept()
            && isEnabled.isKept();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof VacationPatch) {
            VacationPatch that = (VacationPatch) o;

            return Objects.equals(this.subject, that.subject)
                && Objects.equals(this.textBody, that.textBody)
                && Objects.equals(this.htmlBody, that.htmlBody)
                && Objects.equals(this.toDate, that.toDate)
                && Objects.equals(this.fromDate, that.fromDate)
                && Objects.equals(this.isEnabled, that.isEnabled);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject, textBody, htmlBody, toDate, fromDate, isEnabled);
    }
}
