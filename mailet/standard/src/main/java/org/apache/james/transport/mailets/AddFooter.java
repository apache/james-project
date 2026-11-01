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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimePart;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.internet.ParseException;

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
    private static final String CHARSET = "charset";
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
        if (!isParsable(contentType)) {
            // Content handlers fail with unchecked exceptions on such parts (eg the charset of text parts is null)
            LOGGER.debug("Could not parse content type {}, not adding the footer to this part", contentType);
            return false;
        }

        if (part.getContent() instanceof String) {
            Optional<String> content = attachFooterToTextPart(part);
            if (content.isPresent()) {
                String updatedContentType = contentTypeAbleToEncode(contentType, content.get());
                part.setContent(content.get(), updatedContentType);
                part.setHeader(RFC2822Headers.CONTENT_TYPE, updatedContentType);
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

    private boolean isParsable(String contentType) {
        if (contentType == null) {
            // Defaults to text/plain
            return true;
        }
        try {
            new ContentType(contentType);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    /**
     * The footer may hold characters that the charset of the part can not represent, and that would be replaced by
     * '?' when encoding. In such a case, switch the part to UTF-8: the original text is already decoded at this stage.
     */
    private String contentTypeAbleToEncode(String contentType, String content) {
        ContentType parsedContentType;
        try {
            parsedContentType = new ContentType(contentType);
        } catch (ParseException e) {
            LOGGER.debug("Could not parse content type {}, keeping it", contentType, e);
            return contentType;
        }
        String charset = Optional.ofNullable(parsedContentType.getParameter(CHARSET))
            .orElse(StandardCharsets.US_ASCII.name());
        if (canEncode(charset, content)) {
            return contentType;
        }
        parsedContentType.setParameter(CHARSET, StandardCharsets.UTF_8.name());
        return parsedContentType.toString();
    }

    private boolean canEncode(String charset, String content) {
        try {
            return Charset.forName(MimeUtility.javaCharset(charset))
                .newEncoder()
                .canEncode(content);
        } catch (IllegalArgumentException e) {
            // Illegal or unsupported charset name
            return false;
        }
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
         * want to insert out footer immediately prior to the last one, that closes the
         * document: previous ones may belong to a quoted message or to a comment.
         */
        Matcher matcher = BODY_CLOSING_TAG.matcher(content);
        int insertionIndex = -1;
        while (matcher.find()) {
            insertionIndex = matcher.start();
        }
        if (insertionIndex < 0) {
            return content + getFooterHTML();
        }
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
