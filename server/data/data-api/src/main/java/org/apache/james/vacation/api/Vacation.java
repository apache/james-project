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

package org.apache.james.vacation.api;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

import com.google.common.base.Preconditions;

public class Vacation {

    public static final String ID = "singleton";
    public static final boolean DEFAULT_DISABLED = false;

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Optional<Boolean> isEnabled = Optional.empty();
        private Optional<ZonedDateTime> fromDate = Optional.empty();
        private Optional<ZonedDateTime> toDate = Optional.empty();
        private Optional<String> textBody = Optional.empty();
        private Optional<String> subject = Optional.empty();
        private Optional<String> htmlBody = Optional.empty();

        public Builder enabled(boolean enabled) {
            isEnabled = Optional.of(enabled);
            return this;
        }

        public Builder fromDate(Optional<ZonedDateTime> fromDate) {
            Preconditions.checkNotNull(fromDate);
            this.fromDate = fromDate;
            return this;
        }

        public Builder toDate(Optional<ZonedDateTime> toDate) {
            Preconditions.checkNotNull(toDate);
            this.toDate = toDate;
            return this;
        }

        public Builder textBody(Optional<String> textBody) {
            Preconditions.checkNotNull(textBody);
            this.textBody = textBody;
            return this;
        }

        public Builder textBody(String textBody) {
            Preconditions.checkNotNull(textBody);
            this.textBody = Optional.of(textBody);
            return this;
        }

        public Builder subject(Optional<String> subject) {
            Preconditions.checkNotNull(subject);
            this.subject = subject;
            return this;
        }

        public Builder htmlBody(Optional<String> htmlBody) {
            Preconditions.checkNotNull(htmlBody);
            this.htmlBody = htmlBody;
            return this;
        }

        public Builder htmlBody(String htmlBody) {
            Preconditions.checkNotNull(htmlBody);
            this.htmlBody = Optional.of(htmlBody);
            return this;
        }

        public Builder copy(Vacation vacation) {
            this.textBody = vacation.getTextBody();
            this.fromDate = vacation.getFromDate();
            this.toDate = vacation.getToDate();
            this.isEnabled = Optional.of(vacation.isEnabled());
            this.subject = vacation.getSubject();
            return this;
        }

        public Vacation build() {
            boolean enabled = isEnabled.orElse(DEFAULT_DISABLED);
            return new Vacation(enabled, fromDate, toDate, textBody, subject, htmlBody);
        }
    }

    private final boolean isEnabled;
    private final Optional<ZonedDateTime> fromDate;
    private final Optional<ZonedDateTime> toDate;
    private final Optional<String> subject;
    private final Optional<String> textBody;
    private final Optional<String> htmlBody;

    private Vacation(boolean isEnabled, Optional<ZonedDateTime> fromDate, Optional<ZonedDateTime> toDate,
                     Optional<String> textBody, Optional<String> subject, Optional<String> htmlBody) {
        this.isEnabled = isEnabled;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.textBody = textBody;
        this.subject = subject;
        this.htmlBody = htmlBody;
    }


    public boolean isEnabled() {
        return isEnabled;
    }

    public Optional<ZonedDateTime> getFromDate() {
        return fromDate;
    }

    public Optional<ZonedDateTime> getToDate() {
        return toDate;
    }

    public Optional<String> getTextBody() {
        return textBody;
    }

    public Optional<String> getSubject() {
        return subject;
    }

    public Optional<String> getHtmlBody() {
        return htmlBody;
    }

    public boolean isActiveAtDate(ZonedDateTime instant) {
        Preconditions.checkNotNull(instant);
        return isEnabled
            && isAfterOrEqualToFromDate(instant)
            && isBeforeOrEqualToToDate(instant);
    }

    private Boolean isAfterOrEqualToFromDate(ZonedDateTime instant) {
        return fromDate.map(date -> date.isBefore(instant) || date.equals(instant)).orElse(true);
    }

    private Boolean isBeforeOrEqualToToDate(ZonedDateTime instant) {
        return toDate.map(date -> date.isAfter(instant) || date.equals(instant)).orElse(true);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Vacation vacation = (Vacation) o;

        return Objects.equals(this.isEnabled, vacation.isEnabled) &&
            compareZonedDateTimeAcrossTimeZone(this.fromDate, vacation.fromDate) &&
            compareZonedDateTimeAcrossTimeZone(this.toDate, vacation.toDate) &&
            Objects.equals(this.textBody, vacation.textBody) &&
            Objects.equals(this.subject, vacation.subject) &&
            Objects.equals(this.htmlBody, vacation.htmlBody);
    }

    private boolean compareZonedDateTimeAcrossTimeZone(Optional<ZonedDateTime> thisZonedDateTimeOptional, Optional<ZonedDateTime> thatZonedDateTimeOptional) {
        return thisZonedDateTimeOptional.map(thisZonedDateTime -> thatZonedDateTimeOptional
                .map(thisZonedDateTime::isEqual)
                .orElse(false))
            .orElseGet(thatZonedDateTimeOptional::isEmpty);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isEnabled, fromDate, toDate, textBody, subject, htmlBody);
    }

}
