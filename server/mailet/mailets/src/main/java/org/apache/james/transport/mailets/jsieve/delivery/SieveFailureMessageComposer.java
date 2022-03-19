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

package org.apache.james.transport.mailets.jsieve.delivery;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.apache.mailet.Mail;

import com.google.common.base.Strings;

public class SieveFailureMessageComposer {

    public static MimeMessage composeMessage(Mail aMail, Exception ex, String user) throws MessagingException {
        MimeMessage originalMessage = aMail.getMessage();
        MimeMessage message = new MimeMessage(originalMessage);
        MimeMultipart multipart = new MimeMultipart();

        MimeBodyPart noticePart = new MimeBodyPart();
        noticePart.setText("An error was encountered while processing this mail with the active sieve script for user \""
            + user + "\". The error encountered was:\r\n" + ex.getLocalizedMessage() + "\r\n");
        multipart.addBodyPart(noticePart);

        MimeBodyPart originalPart = new MimeBodyPart();
        originalPart.setContent(originalMessage, "message/rfc822");
        if (Strings.isNullOrEmpty(originalMessage.getSubject())) {
            originalPart.setFileName(originalMessage.getSubject().trim());
        } else {
            originalPart.setFileName("No Subject");
        }
        originalPart.setDisposition(MimeBodyPart.INLINE);
        multipart.addBodyPart(originalPart);

        message.setContent(multipart);
        message.setSubject("[SIEVE ERROR] " + originalMessage.getSubject());
        message.setHeader("X-Priority", "1");
        message.saveChanges();
        return message;
    }
}
