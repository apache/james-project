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

import jakarta.inject.Inject;
import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;

import org.apache.commons.io.IOUtils;
import org.apache.james.util.html.HtmlTextExtractor;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

/**
 * This mailet converts HTML parts of a message into Plain text.
 *
 * It starts looking for multipart/alternative containing a text/plain and a text/html part
 * and only keep the text/plain part. Then in a second pass replaces remaining text/html by
 * their textual content, infered by parsing the HTML content and handling common tags.
 *
 * Eg:
 *
 * <mailet matcher="All" class="ToPlainText"/>
 *
 * Only available for servers having JMAP, not available for JPA.
 */
public class ToPlainText extends GenericMailet {
    private final HtmlTextExtractor htmlTextExtractor;

    @Inject
    public ToPlainText(HtmlTextExtractor htmlTextExtractor) {
        this.htmlTextExtractor = htmlTextExtractor;
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        try {
            if (removeHtmlFromMultipartAlternative(mail.getMessage())
                    || convertRemainingHtmlToPlainText(mail.getMessage())) {
                mail.getMessage().saveChanges();
            }
        } catch (Exception e) {
            throw new MessagingException("Exception while extracting HTML", e);
        }
    }

    // true if the message content is mutated
    private boolean removeHtmlFromMultipartAlternative(MimeMessage mimeMessage) throws IOException, MessagingException {
        if (mimeMessage.getContent() instanceof Multipart) {
            Multipart multipart = (Multipart) mimeMessage.getContent();
            return removeHtmlFromMultipartAlternativeForContent(multipart);
        }
        return false;
    }

    // true if the message content is mutated
    private boolean removeHtmlFromMultipartAlternative(BodyPart bodyPart) throws IOException, MessagingException {
        if (bodyPart.getContent() instanceof Multipart) {
            Multipart multipart = (Multipart) bodyPart.getContent();
            return removeHtmlFromMultipartAlternativeForContent(multipart);
        }
        return false;
    }

    // true if the message content is mutated
    private boolean removeHtmlFromMultipartAlternativeForContent(Multipart multipart) throws MessagingException, IOException {
        boolean mutated = false;
        if (multipart.getContentType().startsWith("multipart/alternative")) {
            int removedParts = 0;
            for (int i = 0; i < multipart.getCount(); i++) {
                if (multipart.getBodyPart(i + removedParts).getContentType().startsWith("text/html")) {
                    multipart.removeBodyPart(i + removedParts);
                    removedParts++;
                    mutated = true;
                }
            }
        } else {
            for (int i = 0; i < multipart.getCount(); i++) {
                mutated = removeHtmlFromMultipartAlternative(multipart.getBodyPart(i));
            }
        }
        return mutated;
    }

    // true if the message content is mutated
    private boolean convertRemainingHtmlToPlainText(MimeMessage mimeMessage) throws IOException, MessagingException {
        if (mimeMessage.getContentType().startsWith("text/html")) {
            mimeMessage.setContent(htmlTextExtractor.toPlainText(IOUtils.toString(mimeMessage.getInputStream())), "text/plain");
            return true;
        }
        if (mimeMessage.getContent() instanceof Multipart) {
            Multipart multipart = (Multipart) mimeMessage.getContent();
            return multipartHtmlToText(multipart);
        }
        return false;
    }

    // true if the message content is mutated
    private boolean convertRemainingHtmlToPlainText(BodyPart bodyPart) throws IOException, MessagingException {
        if (bodyPart.getContent() instanceof Multipart) {
            Multipart multipart = (Multipart) bodyPart.getContent();
            return multipartHtmlToText(multipart);
        }
        if (bodyPart.getContentType().startsWith("text/html")) {
            bodyPart.setContent(htmlTextExtractor.toPlainText(IOUtils.toString(bodyPart.getInputStream())), "text/plain");
            return true;
        }
        return false;
    }

    private boolean multipartHtmlToText(Multipart multipart) throws MessagingException, IOException {
        boolean mutated = false;
        for (int i = 0; i < multipart.getCount(); i++) {
            mutated |= convertRemainingHtmlToPlainText(multipart.getBodyPart(i));
        }
        return mutated;
    }

    @Override
    public String getMailetName() {
        return "ToPlainText";
    }
}
