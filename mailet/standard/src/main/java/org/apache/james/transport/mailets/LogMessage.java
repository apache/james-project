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
import java.util.Collections;
import java.util.Enumeration;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.io.IOUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

import com.google.common.io.ByteStreams;

/**
 * Logs Message Headers and/or Body.
 * If the "passThrough" in confs is true the mail will be left untouched in
 * the pipe. If false will be destroyed.  Default is true.
 *
 * @version This is $Revision: 1.8.4.2 $
 */
public class LogMessage extends GenericMailet {

    /**
     * Whether this mailet should allow mails to be processed by additional mailets
     * or mark it as finished.
     */
    private boolean passThrough = true;
    private boolean headers = true;
    private boolean body = true;
    private int bodyMax = 0;
    private String comment = null;

    @Override
    public void init() {
        try {
            passThrough = getInitParameter("passThrough", true);
            headers = getInitParameter("headers", true);
            body = getInitParameter("body", true);
            bodyMax = (getInitParameter("maxBody") == null) ? 0 : Integer.parseInt(getInitParameter("maxBody"));
            comment = getInitParameter("comment");
        } catch (Exception e) {
            log("Caught exception while initializing LogMessage", e);
        }
    }

    @Override
    public String getMailetInfo() {
        return "LogHeaders Mailet";
    }

    @Override
    public void service(Mail mail) {
        log("Logging mail " + mail.getName());
        logComment();
        try {
            MimeMessage message = mail.getMessage();
            logHeaders(message);
            logBody(message);
        } catch (MessagingException | IOException e) {
            log("Error logging message.", e);
        }
        if (!passThrough) {
            mail.setState(Mail.GHOST);
        }
    }

    private void logComment() {
        if (comment != null) {
            log(comment);
        }
    }

    @SuppressWarnings("unchecked")
    private void logHeaders(MimeMessage message) throws MessagingException {
        if (headers) {
            log("\n");
            for (String header : Collections.list((Enumeration<String>) message.getAllHeaderLines())) {
                log(header + "\n");
            }
        }
    }

    private void logBody(MimeMessage message) throws MessagingException, IOException {
        if (body) {
            InputStream inputStream = ByteStreams.limit(message.getRawInputStream(), lengthToLog(message));
            log(IOUtils.toString(inputStream));
        }
    }

    private int lengthToLog(MimeMessage message) throws MessagingException {
        return bodyMax > 0 ? bodyMax : message.getSize();
    }
}
