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

public class ICALAttributeDTO {

    public static final String DEFAULT_SEQUENCE_VALUE = "0";

    public static class Builder {
        public RequiresSender from(Calendar calendar, byte[] originalEvent) {
            String ical = new String(originalEvent, StandardCharsets.UTF_8);
            VEvent vevent = (VEvent) calendar.getComponent("VEVENT");
            Optional<String> uid = optionalOf(vevent.getUid());
            Optional<String> method = optionalOf(calendar.getMethod());
            Optional<String> recurrenceId = optionalOf(vevent.getRecurrenceId());
            Optional<String> sequence = optionalOf(vevent.getSequence());
            Optional<String> dtstamp = optionalOf(vevent.getDateStamp());

            Preconditions.checkNotNull(ical);
            Preconditions.checkState(uid.isPresent(), "uid is a compulsory property of an ICAL object");
            Preconditions.checkState(method.isPresent(), "method is a compulsory property of an ICAL object");
            Preconditions.checkState(dtstamp.isPresent(), "dtstamp is a compulsory property of an ICAL object");

            return sender -> recipient -> replyTo ->
                    new ICALAttributeDTO(
                            ical,
                            uid.get(), sender.asString(),
                            recipient.asString(),
                            replyTo.asString(),
                            dtstamp.get(), method.get(), sequence,
                            recurrenceId);
        }

        private Optional<String> optionalOf(Property property) {
            return Optional.ofNullable(property).map(Property::getValue);
        }

        @FunctionalInterface
        public interface RequiresSender {
            RequiresRecipient sender(MailAddress sender);
        }

        @FunctionalInterface
        public interface RequiresRecipient {
            RequiresReplyTo recipient(MailAddress recipient);
        }

        @FunctionalInterface
        public interface RequiresReplyTo {
            ICALAttributeDTO replyTo(MailAddress replyTo);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final String ical;
    private final String sender;
    private final String recipient;
    private final String replyTo;
    private final String uid;
    private final String dtstamp;
    private final String method;
    private final Optional<String> sequence;
    private final Optional<String> recurrenceId;

    private ICALAttributeDTO(String ical, String uid, String sender, String recipient, String replyTo, String dtstamp,
                             String method, Optional<String> sequence, Optional<String> recurrenceId) {
        this.ical = ical;
        this.sender = sender;
        this.recipient = recipient;
        this.replyTo = replyTo;
        this.uid = uid;
        this.dtstamp = dtstamp;
        this.method = method;
        this.sequence = sequence;
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

    public String getReplyTo() {
        return replyTo;
    }

    public String getUid() {
        return uid;
    }

    public String getDtstamp() {
        return dtstamp;
    }

    public String getMethod() {
        return method;
    }

    public String getSequence() {
        return sequence.orElse(DEFAULT_SEQUENCE_VALUE);
    }

    @JsonProperty("recurrence-id")
    public Optional<String> getRecurrenceId() {
        return recurrenceId;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ICALAttributeDTO) {
            ICALAttributeDTO that = (ICALAttributeDTO) o;
            return Objects.equals(that.ical, this.ical)
                && Objects.equals(that.sender, this.sender)
                && Objects.equals(that.recipient, this.recipient)
                && Objects.equals(that.uid, this.uid)
                && Objects.equals(that.sequence, this.sequence)
                && Objects.equals(that.dtstamp, this.dtstamp)
                && Objects.equals(that.method, this.method)
                && Objects.equals(that.recurrenceId, this.recurrenceId)
                && Objects.equals(that.replyTo, this.replyTo);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(ical, sender, recipient, uid, sequence, dtstamp, method, recurrenceId, replyTo);
    }
}
