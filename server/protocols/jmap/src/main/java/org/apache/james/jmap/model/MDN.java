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

import java.util.Objects;

import org.apache.james.mailbox.model.MessageId;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

@JsonDeserialize(builder = MDN.Builder.class)
public class MDN {

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private MessageId messageId;
        private String subject;
        private String textBody;
        private String reportingUA;
        private MDNDisposition disposition;

        public Builder messageId(MessageId messageId) {
            this.messageId = messageId;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder textBody(String textBody) {
            this.textBody = textBody;
            return this;
        }

        public Builder reportingUA(String reportingUA) {
            this.reportingUA = reportingUA;
            return this;
        }

        public Builder disposition(MDNDisposition disposition) {
            this.disposition = disposition;
            return this;
        }

        public MDN build() {
            Preconditions.checkState(messageId != null, "'messageId' is mandatory");
            Preconditions.checkState(subject != null, "'subject' is mandatory");
            Preconditions.checkState(textBody != null, "'textBody' is mandatory");
            Preconditions.checkState(reportingUA != null, "'reportingUA' is mandatory");
            Preconditions.checkState(disposition != null, "'disposition' is mandatory");

            return new MDN(messageId, subject, textBody, reportingUA, disposition);
        }

    }

    private final MessageId messageId;
    private final String subject;
    private final String textBody;
    private final String reportingUA;
    private final MDNDisposition disposition;

    @VisibleForTesting
    MDN(MessageId messageId, String subject, String textBody, String reportingUA, MDNDisposition disposition) {
        this.messageId = messageId;
        this.subject = subject;
        this.textBody = textBody;
        this.reportingUA = reportingUA;
        this.disposition = disposition;
    }

    public MessageId getMessageId() {
        return messageId;
    }

    public String getSubject() {
        return subject;
    }

    public String getTextBody() {
        return textBody;
    }

    public String getReportingUA() {
        return reportingUA;
    }

    public MDNDisposition getDisposition() {
        return disposition;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MDN) {
            MDN that = (MDN) o;

            return Objects.equals(this.messageId, that.messageId)
                && Objects.equals(this.subject, that.subject)
                && Objects.equals(this.textBody, that.textBody)
                && Objects.equals(this.reportingUA, that.reportingUA)
                && Objects.equals(this.disposition, that.disposition);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(messageId, subject, textBody, reportingUA, disposition);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("messageId", messageId)
            .add("subject", subject)
            .add("textBody", textBody)
            .add("reportingUA", reportingUA)
            .add("mdnDisposition", disposition)
            .toString();
    }
}
