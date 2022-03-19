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



package org.apache.james.transport.mailets;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimePart;

import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.RFC2822Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Takes the message and attaches a footer message to it.  Right now, it only
 * supports simple messages.  Needs to have additions to make it support
 * messages with alternate content types or with attachments.
 */
public class AddFooter extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(AddFooter.class);

    private static final String HTML_BR_TAG = "<br />";
    private static final String CARRIAGE_RETURN = "\r\n";
    private static final Pattern BODY_CLOSING_TAG = Pattern.compile("((?i:</body>))");
    private String plainTextFooter;
    
    @Override
    public void init() throws MessagingException {
        plainTextFooter = getInitParameter("text");
    }

    @Override
    public String getMailetInfo() {
        return "AddFooter Mailet";
    }
    
    @Override
    public void service(Mail mail) throws MessagingException {
        try {
            MimeMessage message = mail.getMessage();

            if (attachFooter(message)) {
                message.saveChanges();
            } else {
                LOGGER.info("Unable to add footer to mail {}", mail.getName());
            }
        } catch (UnsupportedEncodingException e) {
            LOGGER.warn("UnsupportedEncoding Unable to add footer to mail {}", mail.getName(), e);
        } catch (IOException ioe) {
            throw new MessagingException("Could not read message", ioe);
        }
    }
    
    private boolean attachFooter(MimePart part) throws MessagingException, IOException {
        String contentType = part.getContentType();

        if (part.getContent() instanceof String) {
            Optional<String> content = attachFooterToTextPart(part);
            if (content.isPresent()) {
                part.setContent(content.get(), contentType);
                part.setHeader(RFC2822Headers.CONTENT_TYPE, contentType);
                return true;
            }
        }

        if (part.isMimeType("multipart/mixed")
                || part.isMimeType("multipart/related")) {
            MimeMultipart multipart = (MimeMultipart) part.getContent();
            boolean added = attachFooterToFirstPart(multipart);
            if (added) {
                part.setContent(multipart);
            }
            return added;

        } else if (part.isMimeType("multipart/alternative")) {
            MimeMultipart multipart = (MimeMultipart) part.getContent();
            boolean added = attachFooterToAllSubparts(multipart);
            if (added) {
                part.setContent(multipart);
            }
            return added;
        }
        //Give up... we won't attach the footer to this MimePart
        return false;
    }

    /**
     * Prepends the content of the MimePart as text to the existing footer
     *
     * @param part the MimePart to attach
     */
    private String attachFooterToText(String content) throws MessagingException,
            IOException {

        StringBuilder builder = new StringBuilder(content);
        ensureTrailingCarriageReturn(content, builder);
        builder.append(getFooterText());
        return builder.toString();
    }

    private void ensureTrailingCarriageReturn(String content, StringBuilder builder) {
        if (!content.endsWith("\n")) {
            builder.append(CARRIAGE_RETURN);
        }
    }

    /**
     * Prepends the content of the MimePart as HTML to the existing footer
     */
    private String attachFooterToHTML(String content) throws MessagingException,
            IOException {
        
        /* This HTML part may have a closing <BODY> tag.  If so, we
         * want to insert out footer immediately prior to that tag.
         */
        Matcher matcher = BODY_CLOSING_TAG.matcher(content);
        if (!matcher.find()) {
            return content + getFooterHTML();
        }
        int insertionIndex = matcher.start(matcher.groupCount() - 1);
        return new StringBuilder()
                .append(content, 0, insertionIndex)
                .append(getFooterHTML())
                .append(content.substring(insertionIndex))
                .toString();
    }

    private Optional<String> attachFooterToTextPart(MimePart part) throws MessagingException, IOException {
        String content = (String) part.getContent();
        if (part.isMimeType("text/plain")) {
            return Optional.of(attachFooterToText(content));
        } else if (part.isMimeType("text/html")) {
            return Optional.of(attachFooterToHTML(content));
        }
        return Optional.empty();
    }
    
    private boolean attachFooterToFirstPart(MimeMultipart multipart) throws MessagingException, IOException {
        MimeBodyPart firstPart = (MimeBodyPart) multipart.getBodyPart(0);
        return attachFooter(firstPart);
    }

    private boolean attachFooterToAllSubparts(MimeMultipart multipart) throws MessagingException, IOException {
        int count = multipart.getCount();
        boolean isFooterAttached = false;
        for (int index = 0; index < count; index++) {
            MimeBodyPart mimeBodyPart = (MimeBodyPart) multipart.getBodyPart(index);
            isFooterAttached |= attachFooter(mimeBodyPart);
        }
        return isFooterAttached;
    }

    private String getFooterText() {
        return plainTextFooter;
    }

    private String getFooterHTML() {
        String text = getFooterText();
        return HTML_BR_TAG + text.replaceAll(CARRIAGE_RETURN, HTML_BR_TAG);
    }
}
