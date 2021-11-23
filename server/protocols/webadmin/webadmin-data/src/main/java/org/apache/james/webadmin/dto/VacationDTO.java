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

package org.apache.james.webadmin.dto;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.vacation.api.Vacation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;

public class VacationDTO {

    public static VacationDTO from(Vacation vacation) {
        Preconditions.checkNotNull(vacation);
        return new VacationDTO(
            Optional.of(vacation.isEnabled()),
            vacation.getFromDate(),
            vacation.getToDate(),
            vacation.getSubject(),
            vacation.getTextBody(),
            vacation.getHtmlBody());
    }

    private final Optional<Boolean> enabled;
    private final Optional<ZonedDateTime> fromDate;
    private final Optional<ZonedDateTime> toDate;
    private final Optional<String> subject;
    private final Optional<String> textBody;
    private final Optional<String> htmlBody;

    @JsonCreator
    private VacationDTO(@JsonProperty("enabled") Optional<Boolean> enabled,
                        @JsonProperty("fromDate") Optional<ZonedDateTime> fromDate,
                        @JsonProperty("toDate") Optional<ZonedDateTime> toDate,
                        @JsonProperty("subject") Optional<String> subject,
                        @JsonProperty("textBody") Optional<String> textBody,
                        @JsonProperty("htmlBody") Optional<String> htmlBody) {
        this.enabled = enabled;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.textBody = textBody;
        this.subject = subject;
        this.htmlBody = htmlBody;
    }

    public Optional<Boolean> getEnabled() {
        return enabled;
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

    @Override
    public final boolean equals(Object o) {
        if (o instanceof VacationDTO) {
            VacationDTO that = (VacationDTO) o;

            return Objects.equals(this.enabled, that.enabled) && Objects.equals(this.fromDate, that.fromDate)
                && Objects.equals(this.toDate, that.toDate) && Objects.equals(this.subject, that.subject)
                && Objects.equals(this.textBody, that.textBody) && Objects.equals(this.htmlBody, that.htmlBody);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(enabled, fromDate, toDate, subject, textBody, htmlBody);
    }
}
