/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.transport.mailets.managesieve.transcode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Scanner;

import jakarta.mail.Address;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.apache.james.managesieve.api.ManageSieveException;
import org.apache.james.managesieve.api.Session;
import org.apache.james.managesieve.transcode.ManageSieveProcessor;
import org.apache.james.sieverepository.api.exception.SieveRepositoryException;

public class MessageToCoreToMessage {
    
    public interface HelpProvider {
        String getHelp() throws MessagingException;
    }

    protected static String getScript(MimeMessage message) throws IOException, MessagingException {
        String result = null;
        if (message.getContentType().startsWith("multipart/")) {
            MimeMultipart parts = (MimeMultipart) message.getContent();
            boolean found = false;
            // Find the first part with any of:
            // - an attachment type of "application/sieve"
            // - a file suffix of ".siv"
            // - a file suffix of ".sieve"
            for (int i = 0; !found && i < parts.getCount(); i++) {
                MimeBodyPart part = (MimeBodyPart) parts.getBodyPart(i);
                found = part.isMimeType("application/sieve");
                if (!found) {
                    String fileName = null == part.getFileName() ? null : part.getFileName().toLowerCase(Locale.US);
                    found = fileName != null &&
                        (fileName.endsWith(".siv") || fileName.endsWith(".sieve"));
                }
                if (found) {
                    Object content = part.getContent();
                    if (content instanceof String) {
                        return (String) part.getContent();
                    }
                    InputStream is = (InputStream) part.getContent();
                    try (Scanner scanner = new Scanner(is, "UTF-8")) {
                        scanner.useDelimiter("\\A");
                        if (scanner.hasNext()) {
                            result = scanner.next();
                        }
                    }
                }
            }
        }
        if (null == result) {
            throw new MessagingException("Script part not found in this message");
        }
        return result;
    }

    protected static MimeBodyPart toPart(String message) throws MessagingException {
        MimeBodyPart part = new MimeBodyPart();
        part.setText(message);
        part.setDisposition(MimeBodyPart.INLINE);
        return part;
    }

    private final ManageSieveProcessor manageSieveProcessor;
    private final HelpProvider helpProvider;
    
    public MessageToCoreToMessage(ManageSieveProcessor manageSieveProcessor, HelpProvider helpProvider) {
        this.manageSieveProcessor = manageSieveProcessor;
        this.helpProvider = helpProvider;
    }
    
    public MimeMessage execute(Session session, MimeMessage message) throws MessagingException {
        MimeMessage reply = (MimeMessage) message.reply(false);
        reply.setContent(computeMultiPartResponse(session, message));
        if (null == message.getAllRecipients() || 0 >= message.getAllRecipients().length) {
            throw new MessagingException("Message has no recipients");
        } else {
            Address from = message.getAllRecipients()[0];
            reply.setFrom(from);
        }
        reply.saveChanges();
        return reply;
    }

    private MimeMultipart computeMultiPartResponse(Session session, MimeMessage message) throws MessagingException {
        // Extract the command and operands from the subject
        String subject = null == message.getSubject() ? "" : message.getSubject();
        if (subject.startsWith("HELP")) {
            return help();
        }
        String result = computeStringResult(session, message, subject);
        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(toPart(result));
        return multipart;
    }

    protected MimeMultipart help() throws MessagingException {
        MimeMultipart multipart = new MimeMultipart();
        multipart.addBodyPart(toPart(helpProvider.getHelp()));
        return multipart;
    }

    private String computeStringResult(Session session, MimeMessage message, String subject) {
        try {
            return manageSieveProcessor.handleRequest(session,subject + "\r\n" + retrieveAttachedScript(message));
        } catch (ManageSieveException e) {
            return  "NO Manage sieve exception : " + e.getMessage();
        } catch (SieveRepositoryException e) {
            return  "NO SieveRepository exception : " + e.getMessage();
        }
    }

    private String retrieveAttachedScript(MimeMessage message) {
        try {
            return getScript(message);
        } catch (IOException | MessagingException e) {
            return  "";
        }
    }

}
