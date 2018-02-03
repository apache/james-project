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

package org.apache.james.transport.mailets.model;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.core.MailAddress;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.VEvent;

public class ICAL {

    public static final String DEFAULT_SEQUENCE_VALUE = "0";

    public static class Builder {
        private String ical;
        private String sender;
        private String recipient;
        private Optional<String> uid = Optional.empty();
        private Optional<String> sequence = Optional.empty();
        private Optional<String> dtstamp = Optional.empty();
        private Optional<String> method = Optional.empty();
        private Optional<String> recurrenceId = Optional.empty();

        public Builder from(Calendar calendar, byte[] originalEvent) {
            this.ical = new String(originalEvent, StandardCharsets.UTF_8);
            VEvent vevent = (VEvent) calendar.getComponent("VEVENT");
            this.uid = optionalOf(vevent.getUid());
            this.method = optionalOf(calendar.getMethod());
            this.recurrenceId = optionalOf(vevent.getRecurrenceId());
            this.sequence = optionalOf(vevent.getSequence());
            this.dtstamp = optionalOf(vevent.getDateStamp());
            return this;
        }

        private Optional<String> optionalOf(Property property) {
            return Optional.ofNullable(property).map(Property::getValue);
        }

        public Builder sender(String sender) {
            this.sender = sender;
            return this;
        }


        public Builder recipient(MailAddress recipient) {
            this.recipient = recipient.asString();
            return this;
        }

        public ICAL build() {
            Preconditions.checkNotNull(recipient);
            Preconditions.checkNotNull(sender);
            Preconditions.checkNotNull(ical);
            Preconditions.checkState(uid.isPresent(), "uid is a compulsary property of an ICAL object");
            Preconditions.checkState(method.isPresent(), "method is a compulsary property of an ICAL object");
            Preconditions.checkState(dtstamp.isPresent(), "dtstamp is a compulsary property of an ICAL object");
            return new ICAL(ical, sender, recipient, uid, sequence, dtstamp, method, recurrenceId);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final String ical;
    private final String sender;
    private final String recipient;
    private final Optional<String> uid;
    private final Optional<String> sequence;
    private final Optional<String> dtstamp;
    private final Optional<String> method;
    private final Optional<String> recurrenceId;

    private ICAL(String ical, String sender, String recipient, Optional<String> uid, Optional<String> sequence, Optional<String> dtstamp,
                 Optional<String> method, Optional<String> recurrenceId) {
        this.ical = ical;
        this.sender = sender;
        this.recipient = recipient;
        this.uid = uid;
        this.sequence = sequence;
        this.dtstamp = dtstamp;
        this.method = method;
        this.recurrenceId = recurrenceId;
    }

    public String getIcal() {
        return ical;
    }

    public String getSender() {
        return sender;
    }

    public String getRecipient() {
        return recipient;
    }

    public Optional<String> getUid() {
        return uid;
    }

    public String getSequence() {
        return sequence.orElse(DEFAULT_SEQUENCE_VALUE);
    }

    public Optional<String> getDtstamp() {
        return dtstamp;
    }

    public Optional<String> getMethod() {
        return method;
    }

    @JsonProperty("recurrence-id")
    public Optional<String> getRecurrenceId() {
        return recurrenceId;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ICAL) {
            ICAL that = (ICAL) o;
            return Objects.equals(that.ical, this.ical)
                && Objects.equals(that.sender, this.sender)
                && Objects.equals(that.recipient, this.recipient)
                && Objects.equals(that.uid, this.uid)
                && Objects.equals(that.sequence, this.sequence)
                && Objects.equals(that.dtstamp, this.dtstamp)
                && Objects.equals(that.method, this.method)
                && Objects.equals(that.recurrenceId, this.recurrenceId);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(ical, sender, recipient, uid, sequence, dtstamp, method, recurrenceId);
    }
}
