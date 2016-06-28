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

import static org.apache.james.jmap.api.vacation.Vacation.DEFAULT_DISABLED;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.jmap.api.vacation.Vacation;
import org.apache.james.jmap.api.vacation.VacationPatch;
import org.apache.james.jmap.json.OptionalZonedDateTimeDeserializer;
import org.apache.james.jmap.json.OptionalZonedDateTimeSerializer;
import org.apache.james.util.PatchedValue;

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
        private PatchedValue<String> id = PatchedValue.keep();
        private PatchedValue<Boolean> isEnabled = PatchedValue.keep();
        private PatchedValue<ZonedDateTime> fromDate = PatchedValue.keep();
        private PatchedValue<ZonedDateTime> toDate = PatchedValue.keep();
        private PatchedValue<String> subject = PatchedValue.keep();
        private PatchedValue<String> textBody = PatchedValue.keep();
        private PatchedValue<String> htmlBody = PatchedValue.keep();
        private Optional<Boolean> isActivated = Optional.empty();

        public Builder id(String id) {
            this.id = PatchedValue.modifyTo(id);
            return this;
        }

        @JsonProperty("isEnabled")
        public Builder enabled(boolean enabled) {
            isEnabled = PatchedValue.modifyTo(enabled);
            return this;
        }

        @JsonProperty("isActivated")
        public Builder activated(Optional<Boolean> activated) {
            Preconditions.checkNotNull(activated);
            this.isActivated = activated;
            return this;
        }

        @JsonIgnore
        public Builder activated(boolean activated) {
            return activated(Optional.of(activated));
        }

        @JsonDeserialize(using = OptionalZonedDateTimeDeserializer.class)
        public Builder fromDate(Optional<ZonedDateTime> fromDate) {
            this.fromDate = PatchedValue.ofOptional(fromDate);
            return this;
        }

        @JsonDeserialize(using = OptionalZonedDateTimeDeserializer.class)
        public Builder toDate(Optional<ZonedDateTime> toDate) {
            this.toDate = PatchedValue.ofOptional(toDate);
            return this;
        }

        public Builder textBody(Optional<String> textBody) {
            this.textBody = PatchedValue.ofOptional(textBody);
            return this;
        }

        public Builder subject(Optional<String> subject) {
            this.subject = PatchedValue.ofOptional(subject);
            return this;
        }

        public Builder htmlBody(Optional<String> htmlBody) {
            this.htmlBody = PatchedValue.ofOptional(htmlBody);
            return this;
        }

        public Builder fromVacation(Vacation vacation) {
            this.id = PatchedValue.modifyTo(Vacation.ID);
            this.isEnabled = PatchedValue.modifyTo(vacation.isEnabled());
            this.fromDate = PatchedValue.ofOptional(vacation.getFromDate());
            this.toDate = PatchedValue.ofOptional(vacation.getToDate());
            this.textBody = PatchedValue.ofOptional(vacation.getTextBody());
            this.subject = PatchedValue.ofOptional(vacation.getSubject());
            this.htmlBody = PatchedValue.ofOptional(vacation.getHtmlBody());
            return this;
        }

        public VacationResponse build() {
            return new VacationResponse(id, isEnabled, fromDate, toDate, textBody, subject, htmlBody, isActivated);
        }
    }

    private final PatchedValue<String> id;
    private final PatchedValue<Boolean> isEnabled;
    private final PatchedValue<ZonedDateTime> fromDate;
    private final PatchedValue<ZonedDateTime> toDate;
    private final PatchedValue<String> subject;
    private final PatchedValue<String> textBody;
    private final PatchedValue<String> htmlBody;
    private final Optional<Boolean> isActivated;

    private VacationResponse(PatchedValue<String> id, PatchedValue<Boolean> isEnabled, PatchedValue<ZonedDateTime> fromDate, PatchedValue<ZonedDateTime> toDate,
                             PatchedValue<String> textBody, PatchedValue<String> subject, PatchedValue<String> htmlBody, Optional<Boolean> isActivated) {
        this.id = id;
        this.isEnabled = isEnabled;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.textBody = textBody;
        this.subject = subject;
        this.htmlBody = htmlBody;
        this.isActivated = isActivated;
    }

    public String getId() {
        return id.get();
    }

    @JsonProperty("isEnabled")
    public boolean isEnabled() {
        return isEnabled.get();
    }

    @JsonSerialize(using = OptionalZonedDateTimeSerializer.class)
    public Optional<ZonedDateTime> getFromDate() {
        return fromDate.toOptional();
    }

    @JsonSerialize(using = OptionalZonedDateTimeSerializer.class)
    public Optional<ZonedDateTime> getToDate() {
        return toDate.toOptional();
    }

    public Optional<String> getTextBody() {
        return textBody.toOptional();
    }

    public Optional<String> getSubject() {
        return subject.toOptional();
    }

    public Optional<String> getHtmlBody() {
        return htmlBody.toOptional();
    }

    @JsonIgnore
    public boolean isValid() {
        return isMissingOrGoodValue() && !isEnabled.isRemoved();
    }

    @JsonIgnore
    private boolean isMissingOrGoodValue() {
        return id.isKept() || id.toOptional().equals(Optional.of(Vacation.ID));
    }

    @JsonIgnore
    public VacationPatch getPatch() {
        return VacationPatch.builder()
            .fromDate(fromDate)
            .toDate(toDate)
            .htmlBody(htmlBody)
            .textBody(textBody)
            .subject(subject)
            .isEnabled(isEnabled)
            .build();
    }

    @JsonProperty("isActivated")
    public Optional<Boolean> isActivated() {
        return isActivated;
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
            && Objects.equals(this.htmlBody, that.htmlBody)
            && Objects.equals(this.isActivated, that.isActivated);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, isEnabled, fromDate, toDate, textBody, subject, htmlBody, isActivated);
    }
}
