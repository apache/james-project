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

import org.apache.james.util.PatchedValue;

import com.google.common.base.Preconditions;

public class VacationPatch {

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builderFrom(Vacation vacation) {
        return VacationPatch.builder()
            .subject(PatchedValue.ofOptional(vacation.getSubject()))
            .textBody(PatchedValue.ofOptional(vacation.getTextBody()))
            .htmlBody(PatchedValue.ofOptional(vacation.getHtmlBody()))
            .fromDate(PatchedValue.ofOptional(vacation.getFromDate()))
            .toDate(PatchedValue.ofOptional(vacation.getToDate()))
            .isEnabled(PatchedValue.modifyTo(vacation.isEnabled()));
    }

    public static class Builder {
        private PatchedValue<String> subject = PatchedValue.keep();
        private PatchedValue<String> textBody = PatchedValue.keep();
        private PatchedValue<String> htmlBody = PatchedValue.keep();
        private PatchedValue<ZonedDateTime> toDate = PatchedValue.keep();
        private PatchedValue<ZonedDateTime> fromDate = PatchedValue.keep();
        private PatchedValue<Boolean> isEnabled = PatchedValue.keep();

        public Builder subject(PatchedValue<String> subject) {
            Preconditions.checkNotNull(subject);
            this.subject = subject;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = PatchedValue.modifyTo(subject);
            return this;
        }

        public Builder textBody(PatchedValue<String> textBody) {
            Preconditions.checkNotNull(textBody);
            this.textBody = textBody;
            return this;
        }

        public Builder textBody(String textBody) {
            this.textBody = PatchedValue.modifyTo(textBody);
            return this;
        }

        public Builder htmlBody(PatchedValue<String> htmlBody) {
            Preconditions.checkNotNull(htmlBody);
            this.htmlBody = htmlBody;
            return this;
        }

        public Builder htmlBody(String htmlBody) {
            this.htmlBody = PatchedValue.modifyTo(htmlBody);
            return this;
        }

        public Builder toDate(PatchedValue<ZonedDateTime> toDate) {
            Preconditions.checkNotNull(toDate);
            this.toDate = toDate;
            return this;
        }

        public Builder toDate(ZonedDateTime toDate) {
            this.toDate = PatchedValue.modifyTo(toDate);
            return this;
        }

        public Builder fromDate(PatchedValue<ZonedDateTime> fromDate) {
            Preconditions.checkNotNull(fromDate);
            this.fromDate = fromDate;
            return this;
        }

        public Builder fromDate(ZonedDateTime fromDate) {
            this.fromDate = PatchedValue.modifyTo(fromDate);
            return this;
        }

        public Builder isEnabled(PatchedValue<Boolean> isEnabled) {
            Preconditions.checkNotNull(isEnabled);
            this.isEnabled = isEnabled;
            return this;
        }

        public Builder isEnabled(Boolean isEnabled) {
            this.isEnabled = PatchedValue.modifyTo(isEnabled);
            return this;
        }

        public VacationPatch build() {
            return new VacationPatch(subject, textBody, htmlBody, toDate, fromDate, isEnabled);
        }
    }

    private final PatchedValue<String> subject;
    private final PatchedValue<String> textBody;
    private final PatchedValue<String> htmlBody;
    private final PatchedValue<ZonedDateTime> toDate;
    private final PatchedValue<ZonedDateTime> fromDate;
    private final PatchedValue<Boolean> isEnabled;

    private VacationPatch(PatchedValue<String> subject, PatchedValue<String> textBody, PatchedValue<String> htmlBody,
                          PatchedValue<ZonedDateTime> toDate, PatchedValue<ZonedDateTime> fromDate, PatchedValue<Boolean> isEnabled) {
        Preconditions.checkState(!isEnabled.isRemoved());
        this.subject = subject;
        this.textBody = textBody;
        this.htmlBody = htmlBody;
        this.toDate = toDate;
        this.fromDate = fromDate;
        this.isEnabled = isEnabled;
    }

    public PatchedValue<String> getSubject() {
        return subject;
    }

    public PatchedValue<String> getTextBody() {
        return textBody;
    }

    public PatchedValue<String> getHtmlBody() {
        return htmlBody;
    }

    public PatchedValue<ZonedDateTime> getToDate() {
        return toDate;
    }

    public PatchedValue<ZonedDateTime> getFromDate() {
        return fromDate;
    }

    public PatchedValue<Boolean> getIsEnabled() {
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
