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
        private String textBody = "";

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

        public Builder textBody(String textBody) {
            this.textBody = textBody;
            return this;
        }

        public Builder copy(Vacation vacation) {
            this.textBody = vacation.getTextBody();
            this.fromDate = vacation.getFromDate();
            this.toDate = vacation.getToDate();
            this.isEnabled = Optional.of(vacation.isEnabled());
            return this;
        }

        public Vacation build() {
            Preconditions.checkNotNull(textBody);
            return new Vacation(isEnabled.orElse(DEFAULT_DISABLED), fromDate, toDate, textBody);
        }
    }

    private final boolean isEnabled;
    private final Optional<ZonedDateTime> fromDate;
    private final Optional<ZonedDateTime> toDate;
    private final String textBody;

    private Vacation(boolean isEnabled, Optional<ZonedDateTime> fromDate, Optional<ZonedDateTime> toDate, String textBody) {
        this.isEnabled = isEnabled;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.textBody = textBody;
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

    public String getTextBody() {
        return textBody;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Vacation vacation = (Vacation) o;

        return Objects.equals(this.isEnabled, vacation.isEnabled) &&
            Objects.equals(this.fromDate, vacation.fromDate) &&
            Objects.equals(this.toDate, vacation.toDate) &&
            Objects.equals(this.textBody, vacation.textBody);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isEnabled, fromDate, toDate, textBody);
    }

}
