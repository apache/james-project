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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.io.IOUtils;
import org.apache.james.core.MailAddress;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.AttributeName;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.ByteStreams;

/**
 * Logs Message Headers and/or Body.
 * If the "passThrough" in confs is true the mail will be left untouched in
 * the pipe. If false will be destroyed.  Default is true.
 *
 * @version This is $Revision: 1.8.4.2 $
 */
public class LogMessage extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogMessage.class);

    /**
     * Whether this mailet should allow mails to be processed by additional mailets
     * or mark it as finished.
     */
    private final Logger logger;
    private LogLevel logLevel;
    private boolean passThrough = true;
    private boolean headers;
    private boolean body;
    private int bodyMax;
    private String comment;
    private List<String> specificHeaderNames;
    private List<String> specificAttributeNames;


    public LogMessage(Logger logger) {
        this.logger = logger;
    }

    public LogMessage() {
        this(LOGGER);
    }


    @Override
    public void init() {
        try {
            logLevel = getInitParameter("level", LogLevel.class, LogLevel.INFO);
            specificHeaderNames = getInitParameter("specificHeaders", List.class, Collections.emptyList());
            specificAttributeNames = getInitParameter("specificAttributes", List.class, Collections.emptyList());
            headers = getInitParameter("headers", false);
            body = getInitParameter("body", false);
            passThrough = getInitParameter("passThrough", true);
            bodyMax = Optional.ofNullable(getInitParameter("maxBody", Integer.class)).orElse(0);
            comment = getInitParameter("comment");
        } catch (ClassCastException e) {
            logger.error("Caught exception while parsing initial parameter values", e);
        }
    }

    @Override
    public String getMailetInfo() {
        return "LogHeaders Mailet";
    }

    @Override
    public void service(Mail mail) {
        StringBuilder logs = new StringBuilder();
        logs.append(commentLog());
        try {
            logs.append(headersLog(mail));
            logs.append(bodyLog(mail));
            logs.append(attributeLog(mail));
            logLevel.log().accept(logger, logs.toString());
        } catch (MessagingException | IOException e) {
            logger.error("Error logging message.", e);
        }
        if (!passThrough) {
            mail.setState(Mail.GHOST);
        }
    }

    private String attributeLog(Mail mail) {
        StringBuilder attributeLog = new StringBuilder();
        if (!specificAttributeNames.isEmpty()) {
            for (String name : specificAttributeNames) {
                AttributeName attributeName = AttributeName.of(name);
                Optional<Attribute> attribute = mail.getAttribute(attributeName);
                attributeLog.append(name + ": " + attribute.map(Attribute::getValue).map(AttributeValue::getValue).get() + '\n');
            }
            return '\n' + attributeLog.toString();
        }
        return attributeLog.toString();
    }

    private String commentLog() {
        return Optional.ofNullable(comment).orElse("");
    }

    private String headersLog(Mail mail) throws MessagingException {
        StringBuilder headersLog = new StringBuilder();
        if (headers && logger.isInfoEnabled()) {
            MimeMessage message = mail.getMessage();
            headersLog.append(Collections.list(message.getAllHeaderLines()).stream().collect(Collectors.joining("\n")));

            if (!specificHeaderNames.isEmpty()) {
                headersLog.append("\n" + mail.getRecipients().stream().map(recipient -> logSpecificHeadersFor(mail, recipient)).collect(Collectors.joining("\n")));
            }
            return '\n' + headersLog.toString() + '\n';
        }
        return headersLog.toString();
    }

    private String bodyLog(Mail mail) throws MessagingException, IOException {
        MimeMessage message = mail.getMessage();
        if (body && logger.isInfoEnabled()) {
            try (InputStream inputStream = ByteStreams.limit(message.getDataHandler().getInputStream(), lengthToLog(message))) {
                return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private int lengthToLog(MimeMessage message) throws MessagingException {
        return bodyMax > 0 ? bodyMax : messageSizeOrUnlimited(message);
    }

    private int messageSizeOrUnlimited(MimeMessage message) throws MessagingException {
        int computedSize = message.getSize();
        return computedSize > 0 ? computedSize : Integer.MAX_VALUE;
    }

    private String logSpecificHeadersFor(Mail mail, MailAddress recipient) {
        StringBuilder headersLogs = new StringBuilder();
        List<PerRecipientHeaders.Header> headers = mail.getPerRecipientSpecificHeaders()
                .getHeadersForRecipient(recipient).stream()
                .filter(header -> specificHeaderNames.contains(header.getName()))
                .collect(Collectors.toList());
        headersLogs.append("Recipient " + recipient.asPrettyString() + "'s headers are: \n");
        headersLogs.append(headers.stream().map(header -> header.getName() + ": " + header.getValue()).collect(Collectors.joining("\n")));
        return headersLogs.toString();
    }

}
