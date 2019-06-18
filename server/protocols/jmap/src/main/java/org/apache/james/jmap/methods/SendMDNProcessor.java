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

package org.apache.james.jmap.methods;

import static org.apache.james.jmap.methods.Method.JMAP_PREFIX;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.inject.Inject;
import javax.mail.Flags;
import javax.mail.MessagingException;

import org.apache.james.jmap.exceptions.InvalidOriginMessageForMDNException;
import org.apache.james.jmap.exceptions.MessageNotFoundException;
import org.apache.james.jmap.model.JmapMDN;
import org.apache.james.jmap.model.MessageFactory;
import org.apache.james.jmap.model.SetError;
import org.apache.james.jmap.model.SetMessagesRequest;
import org.apache.james.jmap.model.SetMessagesResponse;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.SystemMailboxesProvider;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.OverQuotaException;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.FetchGroupImpl;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mdn.MDN;
import org.apache.james.mdn.MDNReport;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.field.ParseException;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.server.core.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

public class SendMDNProcessor implements SetMessagesProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SendMDNProcessor.class);

    private final MetricFactory metricFactory;
    private final SystemMailboxesProvider systemMailboxesProvider;
    private final MessageIdManager messageIdManager;
    private final MessageAppender messageAppender;
    private final MessageSender messageSender;

    @Inject
    public SendMDNProcessor(MetricFactory metricFactory, SystemMailboxesProvider systemMailboxesProvider,
                            MessageIdManager messageIdManager, MessageAppender messageAppender, MessageSender messageSender) {
        this.metricFactory = metricFactory;
        this.systemMailboxesProvider = systemMailboxesProvider;
        this.messageIdManager = messageIdManager;
        this.messageAppender = messageAppender;
        this.messageSender = messageSender;
    }

    @Override
    public SetMessagesResponse process(SetMessagesRequest request, MailboxSession mailboxSession) {
        return metricFactory.runPublishingTimerMetric(JMAP_PREFIX + "SendMDN",
            () -> handleMDNCreation(request, mailboxSession));
    }

    private SetMessagesResponse handleMDNCreation(SetMessagesRequest request, MailboxSession mailboxSession) {
        return request.getSendMDN()
            .stream()
            .map(MDNCreationEntry -> handleMDNCreation(MDNCreationEntry, mailboxSession))
            .reduce(SetMessagesResponse.builder(), SetMessagesResponse.Builder::mergeWith)
            .build();
    }

    private SetMessagesResponse.Builder handleMDNCreation(ValueWithId.MDNCreationEntry MDNCreationEntry, MailboxSession mailboxSession) {
        try {
            MessageId messageId = sendMdn(MDNCreationEntry, mailboxSession);
            return SetMessagesResponse.builder()
                .mdnSent(MDNCreationEntry.getCreationId(), messageId);
        } catch (InvalidOriginMessageForMDNException e) {
            return SetMessagesResponse.builder()
                .mdnNotSent(MDNCreationEntry.getCreationId(),
                    SetError.builder()
                        .description(String.format("Origin messageId '%s' is invalid." +
                                " A Message Delivery Notification can not be generated for it." +
                                " Explanation: " + e.getExplanation(),
                            MDNCreationEntry.getValue().getMessageId().serialize()))
                        .type(SetError.Type.INVALID_ARGUMENTS)
                        .build());

        } catch (MessageNotFoundException e) {
            return SetMessagesResponse.builder()
                .mdnNotSent(MDNCreationEntry.getCreationId(),
                    SetError.builder()
                        .description(String.format("Message with id %s not found. Thus could not send MDN.",
                            MDNCreationEntry.getValue().getMessageId().serialize()))
                        .type(SetError.Type.INVALID_ARGUMENTS)
                        .build());

        } catch (OverQuotaException e) {
            return SetMessagesResponse.builder()
                .mdnNotSent(MDNCreationEntry.getCreationId(),
                    SetError.builder()
                        .description(e.getMessage())
                        .type(SetError.Type.MAX_QUOTA_REACHED)
                        .build());

        } catch (Exception e) {
            LOGGER.error("Error while sending MDN", e);
            return SetMessagesResponse.builder()
                .mdnNotSent(MDNCreationEntry.getCreationId(),
                    SetError.builder()
                        .description(String.format("Could not send MDN %s", MDNCreationEntry.getCreationId().getId()))
                        .type(SetError.Type.ERROR)
                        .build());
        }
    }

    private MessageId sendMdn(ValueWithId.MDNCreationEntry MDNCreationEntry, MailboxSession mailboxSession)
            throws MailboxException, IOException, MessagingException, ParseException, MessageNotFoundException, InvalidOriginMessageForMDNException {

        JmapMDN mdn = MDNCreationEntry.getValue();
        Message originalMessage = retrieveOriginalMessage(mdn, mailboxSession);
        MDNReport mdnReport = mdn.generateReport(originalMessage, mailboxSession);
        List<MessageAttachment> reportAsAttachment = ImmutableList.of(convertReportToAttachment(mdnReport));

        Message mdnAnswer = mdn.generateMDNMessage(originalMessage, mailboxSession);

        Flags seen = new Flags(Flags.Flag.SEEN);
        MessageFactory.MetaDataWithContent metaDataWithContent = messageAppender.appendMessageInMailbox(mdnAnswer,
            getOutbox(mailboxSession), reportAsAttachment, seen, mailboxSession);

        messageSender.sendMessage(metaDataWithContent,
            Envelope.fromMime4JMessage(mdnAnswer), mailboxSession);

        return metaDataWithContent.getMessageId();
    }

    private Message retrieveOriginalMessage(JmapMDN mdn, MailboxSession mailboxSession) throws MailboxException, IOException, MessageNotFoundException {
        List<MessageResult> messages = messageIdManager.getMessages(ImmutableList.of(mdn.getMessageId()),
            FetchGroupImpl.HEADERS,
            mailboxSession);

        if (messages.size() == 0) {
            throw new MessageNotFoundException();
        }

        DefaultMessageBuilder messageBuilder = new DefaultMessageBuilder();
        messageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE);
        messageBuilder.setDecodeMonitor(DecodeMonitor.SILENT);
        return messageBuilder.parseMessage(messages.get(0).getHeaders().getInputStream());
    }

    private MessageAttachment convertReportToAttachment(MDNReport mdnReport) {
        Attachment attachment = Attachment.builder()
            .bytes(mdnReport.formattedValue().getBytes(StandardCharsets.UTF_8))
            .type(MDN.DISPOSITION_CONTENT_TYPE)
            .build();

        return MessageAttachment.builder()
            .attachment(attachment)
            .isInline(true)
            .build();
    }


    private MessageManager getOutbox(MailboxSession mailboxSession) throws MailboxException {
        return systemMailboxesProvider.getMailboxByRole(Role.OUTBOX, mailboxSession.getUser())
            .findAny()
            .orElseThrow(() -> new IllegalStateException("User don't have an Outbox"));
    }

}
