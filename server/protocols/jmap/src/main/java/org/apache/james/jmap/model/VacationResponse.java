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

package org.apache.james.jmap.model;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.jmap.api.vacation.Vacation;
import org.apache.james.jmap.json.OptionalZonedDateTimeDeserializer;
import org.apache.james.jmap.json.OptionalZonedDateTimeSerializer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Preconditions;

@JsonDeserialize(builder = VacationResponse.Builder.class)
public class VacationResponse {

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private String id;
        private boolean isEnabled = false;
        private Optional<ZonedDateTime> fromDate = Optional.empty();
        private Optional<ZonedDateTime> toDate = Optional.empty();
        private Optional<String> subject = Optional.empty();
        private Optional<String> textBody = Optional.empty();
        private Optional<String> htmlBody = Optional.empty();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        @JsonProperty("isEnabled")
        public Builder enabled(boolean enabled) {
            isEnabled = enabled;
            return this;
        }

        @JsonDeserialize(using = OptionalZonedDateTimeDeserializer.class)
        public Builder fromDate(Optional<ZonedDateTime> fromDate) {
            this.fromDate = fromDate;
            return this;
        }

        @JsonDeserialize(using = OptionalZonedDateTimeDeserializer.class)
        public Builder toDate(Optional<ZonedDateTime> toDate) {
            this.toDate = toDate;
            return this;
        }

        public Builder textBody(Optional<String> textBody) {
            this.textBody = textBody;
            return this;
        }

        public Builder subject(Optional<String> subject) {
            this.subject = subject;
            return this;
        }

        public Builder htmlBody(Optional<String> htmlBody) {
            this.htmlBody = htmlBody;
            return this;
        }

        public Builder fromVacation(Vacation vacation) {
            this.id = Vacation.ID;
            this.isEnabled = vacation.isEnabled();
            this.fromDate = vacation.getFromDate();
            this.toDate = vacation.getToDate();
            this.textBody = vacation.getTextBody();
            this.subject = vacation.getSubject();
            this.htmlBody = vacation.getHtmlBody();
            return this;
        }

        public VacationResponse build() {
            Preconditions.checkState(textBody.isPresent() || htmlBody.isPresent() || !isEnabled, "textBody or htmlBody property of vacationResponse object should not be null when enabled");
            return new VacationResponse(id, isEnabled, fromDate, toDate, textBody, subject, htmlBody);
        }
    }

    private final String id;
    private final boolean isEnabled;
    private final Optional<ZonedDateTime> fromDate;
    private final Optional<ZonedDateTime> toDate;
    private final Optional<String> subject;
    private final Optional<String> textBody;
    private final Optional<String> htmlBody;

    private VacationResponse(String id, boolean isEnabled, Optional<ZonedDateTime> fromDate, Optional<ZonedDateTime> toDate,
                             Optional<String> textBody, Optional<String> subject, Optional<String> htmlBody) {
        this.id = id;
        this.isEnabled = isEnabled;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.textBody = textBody;
        this.subject = subject;
        this.htmlBody = htmlBody;
    }

    public String getId() {
        return id;
    }

    @JsonProperty("isEnabled")
    public boolean isEnabled() {
        return isEnabled;
    }

    @JsonSerialize(using = OptionalZonedDateTimeSerializer.class)
    public Optional<ZonedDateTime> getFromDate() {
        return fromDate;
    }

    @JsonSerialize(using = OptionalZonedDateTimeSerializer.class)
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

    @JsonIgnore
    public boolean isValid() {
        return id.equals(Vacation.ID);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        VacationResponse that = (VacationResponse) o;

        return Objects.equals(this.id, that.id)
            && Objects.equals(this.isEnabled, that.isEnabled)
            && Objects.equals(this.fromDate, that.fromDate)
            && Objects.equals(this.toDate, that.toDate)
            && Objects.equals(this.textBody, that.textBody)
            && Objects.equals(this.subject, that.subject)
            && Objects.equals(this.htmlBody, that.htmlBody);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, isEnabled, fromDate, toDate, textBody, subject, htmlBody);
    }
}
