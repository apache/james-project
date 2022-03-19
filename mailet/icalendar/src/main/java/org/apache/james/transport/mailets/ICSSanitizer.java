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

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import jakarta.mail.BodyPart;
import jakarta.mail.Header;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

/**
 * Some senders embed 'text/calendar' content as part of Mime bodypart headers with an empty body.
 *
 * This mailet duplicate the 'text/calendar' content to the Mime body part.
 *
 * Example configuration:
 *
 * &lt;mailet match="All" class="ICSSanitizer"/&gt;
 */
public class ICSSanitizer extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(ICSSanitizer.class);
    private static final int TEXT_PREFIX_SIZE = 5;
    public static final String DEFAULT_FILENAME = "event.ics";

    @Override
    public void service(Mail mail) {
        try {
            MimeMessage mimeMessage = mail.getMessage();

            if (mimeMessage.getContent() instanceof Multipart) {
                Multipart multipart = (Multipart) mimeMessage.getContent();

                if (needsSanitizing(multipart)) {
                    mimeMessage.setContent(sanitize(multipart));
                    mimeMessage.saveChanges();
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Could not sanitize {}", mail.getName(), e);
        }
    }

    private boolean needsSanitizing(Multipart multipart) throws MessagingException {
        return bodyPartStream(multipart)
            .anyMatch(Throwing.predicate(this::needsSanitizing));
    }

    private boolean needsSanitizing(BodyPart bodyPart) throws MessagingException {
        return bodyPart.isMimeType("text/calendar") && bodyPart.getSize() <= 0;
    }

    private MimeMultipart sanitize(Multipart multipart) throws MessagingException {
        MimeMultipart mimeMultipart = new MimeMultipart();
        bodyPartStream(multipart)
            .map(Throwing.function(this::sanitize))
            .forEach(Throwing.consumer(mimeMultipart::addBodyPart));
        return mimeMultipart;
    }

    private BodyPart sanitize(BodyPart bodyPart) throws MessagingException {
        if (needsSanitizing(bodyPart)
                && bodyPart instanceof MimeBodyPart) {
            MimeBodyPart mimeBodyPart = (MimeBodyPart) bodyPart;
            mimeBodyPart.setText(
                computeBodyFromOriginalCalendar(bodyPart),
                StandardCharsets.UTF_8.name(),
                bodyPart.getContentType().substring(TEXT_PREFIX_SIZE));
            setFileNameIfNeeded(mimeBodyPart);
        }
        return bodyPart;
    }

    private void setFileNameIfNeeded(MimeBodyPart mimeBodyPart) throws MessagingException {
        if (mimeBodyPart.getFileName() == null) {
            mimeBodyPart.setFileName(DEFAULT_FILENAME);
        }
    }

    private String computeBodyFromOriginalCalendar(BodyPart bodyPart) throws MessagingException {
        return headerStream(bodyPart)
            .map(header -> header.getName() + ": " + header.getValue())
            .collect(Collectors.joining("\r\n"));
    }

    private Stream<Header> headerStream(BodyPart bodyPart) throws MessagingException {
        return Collections.list(bodyPart.getAllHeaders()).stream();
    }

    private Stream<BodyPart> bodyPartStream(Multipart multipart) throws MessagingException {
        return IntStream.range(0, multipart.getCount())
            .boxed()
            .map(Throwing.function(multipart::getBodyPart));
    }
}
