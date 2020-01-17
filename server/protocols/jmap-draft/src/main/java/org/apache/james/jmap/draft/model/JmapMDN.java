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

package org.apache.james.jmap.draft.model;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.jmap.draft.exceptions.InvalidOriginMessageForMDNException;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mdn.MDN;
import org.apache.james.mdn.MDNReport;
import org.apache.james.mdn.fields.Disposition;
import org.apache.james.mdn.fields.ReportingUserAgent;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.address.AddressList;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.address.MailboxList;
import org.apache.james.mime4j.dom.field.AddressListField;
import org.apache.james.mime4j.dom.field.ParseException;
import org.apache.james.mime4j.field.AddressListFieldLenientImpl;
import org.apache.james.mime4j.util.MimeUtil;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

@JsonDeserialize(builder = JmapMDN.Builder.class)
public class JmapMDN {

    public static final String DISPOSITION_NOTIFICATION_TO = "Disposition-Notification-To";
    public static final String RETURN_PATH = "Return-Path";

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

        public JmapMDN build() {
            Preconditions.checkState(messageId != null, "'messageId' is mandatory");
            Preconditions.checkState(subject != null, "'subject' is mandatory");
            Preconditions.checkState(textBody != null, "'textBody' is mandatory");
            Preconditions.checkState(reportingUA != null, "'reportingUA' is mandatory");
            Preconditions.checkState(disposition != null, "'disposition' is mandatory");

            return new JmapMDN(messageId, subject, textBody, reportingUA, disposition);
        }

    }

    private final MessageId messageId;
    private final String subject;
    private final String textBody;
    private final String reportingUA;
    private final MDNDisposition disposition;

    @VisibleForTesting
    JmapMDN(MessageId messageId, String subject, String textBody, String reportingUA, MDNDisposition disposition) {
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

    public ReportingUserAgent getReportingUA() {
        return ReportingUserAgent.builder().userAgentName(reportingUA).build();
    }

    public MDNDisposition getDisposition() {
        return disposition;
    }

    public Message generateMDNMessage(Message originalMessage, MailboxSession mailboxSession) throws ParseException, IOException, InvalidOriginMessageForMDNException {

        Username username = mailboxSession.getUser();

        return MDN.builder()
            .report(generateReport(originalMessage, mailboxSession))
            .humanReadableText(textBody)
            .build()
        .asMime4JMessageBuilder()
            .setTo(getSenderAddress(originalMessage))
            .setFrom(username.asString())
            .setSubject(subject)
            .setMessageId(MimeUtil.createUniqueMessageId(username.getDomainPart().map(Domain::name).orElse(null)))
            .build();
    }

    private String getSenderAddress(Message originalMessage) throws InvalidOriginMessageForMDNException {
        return getAddressForHeader(originalMessage, DISPOSITION_NOTIFICATION_TO)
            .orElseThrow(() -> InvalidOriginMessageForMDNException.missingHeader(DISPOSITION_NOTIFICATION_TO))
            .getAddress();
    }

    private Optional<Mailbox> getAddressForHeader(Message originalMessage, String fieldName) {
        return Optional.ofNullable(originalMessage.getHeader()
            .getFields(fieldName))
            .orElse(ImmutableList.of())
            .stream()
            .map(field -> AddressListFieldLenientImpl.PARSER.parse(field, new DecodeMonitor()))
            .findFirst()
            .map(AddressListField::getAddressList)
            .map(AddressList::flatten)
            .map(MailboxList::stream)
            .orElse(Stream.of())
            .findFirst();
    }

    private MDNReport generateReport(Message originalMessage, MailboxSession mailboxSession) throws InvalidOriginMessageForMDNException {
        if (originalMessage.getMessageId() == null) {
            throw InvalidOriginMessageForMDNException.missingHeader("Message-ID");
        }
        return MDNReport.builder()
            .dispositionField(generateDisposition())
            .originalRecipientField(mailboxSession.getUser().asString())
            .originalMessageIdField(originalMessage.getMessageId())
            .finalRecipientField(mailboxSession.getUser().asString())
            .reportingUserAgentField(getReportingUA())
            .build();
    }

    private Disposition generateDisposition() {
        return Disposition.builder()
            .actionMode(disposition.getActionMode())
            .sendingMode(disposition.getSendingMode())
            .type(disposition.getType())
            .build();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof JmapMDN) {
            JmapMDN that = (JmapMDN) o;

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
