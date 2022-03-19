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
import java.util.Collections;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.commons.io.IOUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
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
    private boolean passThrough = true;
    private boolean headers = true;
    private boolean body = true;
    private int bodyMax = 0;
    private String comment = null;

    public LogMessage(Logger logger) {
        this.logger = logger;
    }

    public LogMessage() {
        this(LOGGER);
    }

    @Override
    public void init() {
        try {
            passThrough = getInitParameter("passThrough", true);
            headers = getInitParameter("headers", true);
            body = getInitParameter("body", true);
            bodyMax = (getInitParameter("maxBody") == null) ? 0 : Integer.parseInt(getInitParameter("maxBody"));
            comment = getInitParameter("comment");
        } catch (Exception e) {
            logger.error("Caught exception while initializing LogMessage", e);
        }
    }

    @Override
    public String getMailetInfo() {
        return "LogHeaders Mailet";
    }

    @Override
    public void service(Mail mail) {
        logger.info("Logging mail {}", mail.getName());
        logComment();
        try {
            MimeMessage message = mail.getMessage();
            logHeaders(message);
            logBody(message);
        } catch (MessagingException | IOException e) {
            logger.error("Error logging message.", e);
        }
        if (!passThrough) {
            mail.setState(Mail.GHOST);
        }
    }

    private void logComment() {
        if (comment != null) {
            logger.info(comment);
        }
    }

    private void logHeaders(MimeMessage message) throws MessagingException {
        if (headers && logger.isInfoEnabled()) {
            logger.info("\n");
            for (String header : Collections.list(message.getAllHeaderLines())) {
                logger.info(header + "\n");
            }
        }
    }

    private void logBody(MimeMessage message) throws MessagingException, IOException {
        if (body && logger.isInfoEnabled()) {
            try (InputStream inputStream = ByteStreams.limit(message.getDataHandler().getInputStream(), lengthToLog(message))) {
                logger.info(IOUtils.toString(inputStream, StandardCharsets.UTF_8));
            }
        }
    }

    private int lengthToLog(MimeMessage message) throws MessagingException {
        return bodyMax > 0 ? bodyMax : messageSizeOrUnlimited(message);
    }

    private int messageSizeOrUnlimited(MimeMessage message) throws MessagingException {
        int computedSize = message.getSize();
        if (computedSize > 0) {
            return computedSize;
        }
        return Integer.MAX_VALUE;
    }
}
