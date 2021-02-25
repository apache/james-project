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
package org.apache.james.jmap.mailet;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MultimailboxesSearchQuery;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mdn.MDNReport;
import org.apache.james.mdn.MDNReportParser;
import org.apache.james.mdn.fields.OriginalMessageId;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.SingleBody;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.server.core.MimeMessageInputStream;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;

import reactor.core.publisher.Flux;
import scala.util.Try;

/**
 * This mailet handles MDN messages and define a header X-JAMES-MDN-JMAP-MESSAGE-ID referencing
 * the original message (by its Jmap Id) asking for the recipient to send an MDN.
 */
public class ExtractMDNOriginalJMAPMessageId extends GenericMailet {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtractMDNOriginalJMAPMessageId.class);

    private static final String MESSAGE_DISPOSITION_NOTIFICATION = "message/disposition-notification";
    private static final String X_JAMES_MDN_JMAP_MESSAGE_ID = "X-JAMES-MDN-JMAP-MESSAGE-ID";

    private final MailboxManager mailboxManager;
    private final UsersRepository usersRepository;

    @Inject
    public ExtractMDNOriginalJMAPMessageId(MailboxManager mailboxManager, UsersRepository usersRepository) {
        this.mailboxManager = mailboxManager;
        this.usersRepository = usersRepository;
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        if (mail.getRecipients().size() != 1) {
            LOGGER.warn("MDN should only be sent to a single recipient");
            return;
        }
        MailAddress recipient = Iterables.getOnlyElement(mail.getRecipients());
        MimeMessage mimeMessage = mail.getMessage();

        findReport(mimeMessage)
            .flatMap(this::parseReport)
            .flatMap(MDNReport::getOriginalMessageIdField)
            .map(OriginalMessageId::getOriginalMessageId)
            .flatMap(messageId -> findMessageIdForRFC822MessageId(messageId, recipient))
            .ifPresent(messageId -> setJmapMessageIdAsHeader(mimeMessage, messageId));
    }

    private void setJmapMessageIdAsHeader(MimeMessage mimeMessage, MessageId messageId) {
        LOGGER.debug("Adding header {}:{}", X_JAMES_MDN_JMAP_MESSAGE_ID, messageId.serialize());
        try {
            mimeMessage.addHeader(X_JAMES_MDN_JMAP_MESSAGE_ID, messageId.serialize());
        } catch (MessagingException e) {
            LOGGER.error("unable to add " + X_JAMES_MDN_JMAP_MESSAGE_ID + " header to message", e);
        }
    }

    private Optional<MessageId> findMessageIdForRFC822MessageId(String messageId, MailAddress recipient) {
        LOGGER.debug("Searching message {} for recipient {}", messageId, recipient.asPrettyString());
        try {
            MailboxSession session = mailboxManager.createSystemSession(usersRepository.getUsername(recipient));
            int limit = 1;
            MultimailboxesSearchQuery searchByRFC822MessageId = MultimailboxesSearchQuery
                .from(SearchQuery.of(SearchQuery.mimeMessageID(messageId)))
                .build();
            return Flux.from(mailboxManager.search(searchByRFC822MessageId, session, limit)).toStream().findFirst();
        } catch (MailboxException | UsersRepositoryException e) {
            LOGGER.error("unable to find message with Message-Id: " + messageId, e);
        }
        return Optional.empty();
    }

    private Optional<MDNReport> parseReport(Entity report) {
        LOGGER.debug("Parsing report");
        try (InputStream inputStream = ((SingleBody) report.getBody()).getInputStream()) {
            Try<MDNReport> result = MDNReportParser.parse(inputStream, report.getCharset());
            if (result.isSuccess()) {
                return Optional.of(result.get());
            } else {
                LOGGER.error("unable to parse MESSAGE_DISPOSITION_NOTIFICATION part", result.failed().get());
                return Optional.empty();
            }
        } catch (IOException e) {
            LOGGER.error("unable to parse MESSAGE_DISPOSITION_NOTIFICATION part", e);
            return Optional.empty();
        }
    }

    private Optional<Entity> findReport(MimeMessage mimeMessage) {
        return parseMessage(mimeMessage).flatMap(this::extractReport);
    }

    @VisibleForTesting Optional<Entity> extractReport(Message message) {
        LOGGER.debug("Extracting report");
        if (!message.isMultipart()) {
            LOGGER.debug("MDN Message must be multipart");
            return Optional.empty();
        }
        List<Entity> bodyParts = ((Multipart) message.getBody()).getBodyParts();
        if (bodyParts.size() < 2) {
            LOGGER.debug("MDN Message must contain at least two parts");
            return Optional.empty();
        }
        Entity report = bodyParts.get(1);
        if (!isDispositionNotification(report)) {
            LOGGER.debug("MDN Message second part must be of type " + MESSAGE_DISPOSITION_NOTIFICATION);
            return Optional.empty();
        }
        return Optional.of(report);
    }

    private boolean isDispositionNotification(Entity entity) {
        return entity
            .getMimeType()
            .startsWith(MESSAGE_DISPOSITION_NOTIFICATION);
    }

    private Optional<Message> parseMessage(MimeMessage mimeMessage) {
        LOGGER.debug("Parsing message");
        try {
            Message message = new DefaultMessageBuilder()
                .parseMessage(new MimeMessageInputStream(mimeMessage));
            return Optional.of(message);
        } catch (IOException | MessagingException e) {
            LOGGER.error("unable to parse message", e);
            return Optional.empty();
        }
    }

    @Override
    public String getMailetInfo() {
        return "ExtractMDNOriginalJMAPMessageId";
    }

}
