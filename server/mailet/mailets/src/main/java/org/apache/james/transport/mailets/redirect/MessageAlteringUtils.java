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
package org.apache.james.transport.mailets.redirect;

import java.io.ByteArrayOutputStream;
import java.util.Enumeration;

import jakarta.mail.BodyPart;
import jakarta.mail.Header;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.apache.james.server.core.MimeMessageUtil;
import org.apache.james.transport.mailets.utils.MimeMessageUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.base.RFC2822Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class MessageAlteringUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageAlteringUtils.class);
    private static final char LINE_BREAK = '\n';

    public static Builder from(RedirectNotify mailet) {
        return new Builder(mailet);
    }

    public static class Builder {

        private RedirectNotify mailet;
        private Mail originalMail;

        private Builder(RedirectNotify mailet) {
            this.mailet = mailet;
        }

        public Builder originalMail(Mail originalMail) {
            this.originalMail = originalMail;
            return this;
        }


        public MimeMessage alteredMessage() throws MessagingException {
            return build().alteredMessage();
        }

        @VisibleForTesting MessageAlteringUtils build() {
            Preconditions.checkNotNull(mailet, "'mailet' is mandatory");
            Preconditions.checkNotNull(originalMail, "'originalMail' is mandatory");
            return new MessageAlteringUtils(mailet, originalMail);
        }
    }

    private final RedirectNotify mailet;
    private final Mail originalMail;

    private MessageAlteringUtils(RedirectNotify mailet, Mail originalMail) {
        this.mailet = mailet;
        this.originalMail = originalMail;
    }

    /**
     * Builds the message of the newMail in case it has to be altered.
     */
    private MimeMessage alteredMessage() throws MessagingException {
        MimeMessage originalMessage = originalMail.getMessage();
        MimeMessage newMessage = new MimeMessage(Session.getDefaultInstance(System.getProperties(), null));

        // Copy the relevant headers
        copyRelevantHeaders(originalMessage, newMessage);

        String head = new MimeMessageUtils(originalMessage).getMessageHeaders();
        try {
            MimeMultipart multipart = generateMultipartContent(originalMessage, head);

            newMessage.setContent(multipart);
            newMessage.setHeader(RFC2822Headers.CONTENT_TYPE, multipart.getContentType());
            return newMessage;
        } catch (Exception ioe) {
            throw new MessagingException("Unable to create multipart body", ioe);
        }
    }

    private MimeMultipart generateMultipartContent(MimeMessage originalMessage, String head) throws Exception {
        // Create the message body
        MimeMultipart multipart = new MimeMultipart("mixed");

        // Create the message
        MimeMultipart mpContent = new MimeMultipart("alternative");
        mpContent.addBodyPart(getBodyPart(originalMail, originalMessage, head));

        MimeBodyPart contentPartRoot = new MimeBodyPart();
        contentPartRoot.setContent(mpContent);

        multipart.addBodyPart(contentPartRoot);

        if (mailet.getInitParameters().isDebug()) {
            LOGGER.debug("attachmentType:{}", mailet.getInitParameters().getAttachmentType());
        }
        if (!mailet.getInitParameters().getAttachmentType().equals(TypeCode.NONE)) {
            multipart.addBodyPart(getAttachmentPart(originalMessage, head));
        }

        if (mailet.getInitParameters().isAttachError() && originalMail.getErrorMessage() != null) {
            multipart.addBodyPart(getErrorPart(originalMail));
        }
        return multipart;
    }

    private BodyPart getBodyPart(Mail originalMail, MimeMessage originalMessage, String head) throws MessagingException, Exception {
        MimeBodyPart part = new MimeBodyPart();
        part.setText(getText(originalMail, originalMessage, head));
        part.setDisposition(jakarta.mail.Part.INLINE);
        return part;
    }

    private MimeBodyPart getAttachmentPart(MimeMessage originalMessage, String head) throws MessagingException, Exception {
        MimeBodyPart attachmentPart = new MimeBodyPart();
        switch (mailet.getInitParameters().getAttachmentType()) {
            case HEADS:
                attachmentPart.setText(head);
                break;
            case BODY:
                try {
                    attachmentPart.setText(getMessageBody(originalMessage));
                } catch (Exception e) {
                    attachmentPart.setText("body unavailable");
                }
                break;
            case ALL:
                attachmentPart.setText(head + "\r\nMessage:\r\n" + getMessageBody(originalMessage));
                break;
            case MESSAGE:
                attachmentPart.setContent(originalMessage, "message/rfc822");
                break;
            case NONE:
                break;
            case UNALTERED:
                break;
        }
        attachmentPart.setFileName(getFileName(originalMessage.getSubject()));
        attachmentPart.setDisposition(jakarta.mail.Part.ATTACHMENT);
        return attachmentPart;
    }

    @VisibleForTesting String getFileName(String subject) {
        if (subject != null && !subject.trim().isEmpty()) {
            return subject.trim();
        }
        return "No Subject";
    }

    private MimeBodyPart getErrorPart(Mail originalMail) throws MessagingException {
        MimeBodyPart errorPart = new MimeBodyPart();
        errorPart.setContent(originalMail.getErrorMessage(), "text/plain");
        errorPart.setHeader(RFC2822Headers.CONTENT_TYPE, "text/plain");
        errorPart.setFileName("Reasons");
        errorPart.setDisposition(jakarta.mail.Part.ATTACHMENT);
        return errorPart;
    }

    private String getText(Mail originalMail, MimeMessage originalMessage, String head) throws MessagingException {
        StringBuilder builder = new StringBuilder();

        String messageText = mailet.getMessage(originalMail);
        if (messageText != null) {
            builder.append(messageText)
                .append(LINE_BREAK);
        }

        if (mailet.getInitParameters().isDebug()) {
            LOGGER.debug("inline:{}", mailet.getInitParameters().getInLineType());
        }
        switch (mailet.getInitParameters().getInLineType()) {
            case ALL:
                builder.append(headText(head));
                builder.append(bodyText(originalMessage));
                break;
            case HEADS:
                builder.append(headText(head));
                break;
            case BODY:
                builder.append(bodyText(originalMessage));
                break;
            case NONE:
                break;
            case MESSAGE:
                break;
            case UNALTERED:
                break;
        }
        return builder.toString();
    }

    private String headText(String head) {
        return "Message Headers:" + LINE_BREAK + head + LINE_BREAK;
    }

    private String bodyText(MimeMessage originalMessage) {
        StringBuilder builder = new StringBuilder();
        builder.append("Message:")
            .append(LINE_BREAK);
        try {
            builder.append(getMessageBody(originalMessage))
                .append(LINE_BREAK);
        } catch (Exception e) {
            builder.append("body unavailable")
                .append(LINE_BREAK);
        }
        return builder.toString();
    }

    /**
     * Utility method for obtaining a string representation of a Message's body
     */
    private String getMessageBody(MimeMessage message) throws Exception {
        ByteArrayOutputStream bodyOs = new ByteArrayOutputStream();
        MimeMessageUtil.writeMessageBodyTo(message, bodyOs);
        return bodyOs.toString();
    }

    private void copyRelevantHeaders(MimeMessage originalMessage, MimeMessage newMessage) throws MessagingException {
        Enumeration<Header> headerEnum = originalMessage.getMatchingHeaders(
                new String[] { RFC2822Headers.DATE, RFC2822Headers.FROM, RFC2822Headers.REPLY_TO, RFC2822Headers.TO, 
                        RFC2822Headers.SUBJECT, RFC2822Headers.RETURN_PATH });
        while (headerEnum.hasMoreElements()) {
            Header header = headerEnum.nextElement();
            newMessage.addHeader(header.getName(), header.getValue());
        }
    }

}
