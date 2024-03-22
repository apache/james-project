/****************************************************************
 O * Licensed to the Apache Software Foundation (ASF) under one   *
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

package org.apache.james.transport.util;

import java.io.IOException;
import java.util.Optional;

import jakarta.activation.DataHandler;
import jakarta.inject.Inject;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;

import org.apache.james.mime4j.dom.field.ContentTypeField;
import org.apache.james.util.html.HtmlTextExtractor;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class MimeMessageBodyGenerator {
    public static final String ALTERNATIVE = "alternative";
    public static final String EMPTY_TEXT = "";

    private final HtmlTextExtractor htmlTextExtractor;

    @Inject
    @VisibleForTesting
    public MimeMessageBodyGenerator(HtmlTextExtractor htmlTextExtractor) {
        this.htmlTextExtractor = htmlTextExtractor;
    }

    public MimeMessage from(MimeMessage messageHoldingHeaders, Optional<String> plainText, Optional<String> htmlText) throws MessagingException {
        Preconditions.checkNotNull(messageHoldingHeaders);
        Preconditions.checkNotNull(plainText);
        Preconditions.checkNotNull(htmlText);
        if (htmlText.isPresent()) {
            messageHoldingHeaders.setContent(generateMultipart(htmlText.get(), plainText));
        } else {
            messageHoldingHeaders.setText(plainText.orElse(EMPTY_TEXT));
        }
        return messageHoldingHeaders;
    }

    private Multipart generateMultipart(String htmlText, Optional<String> plainText) throws MessagingException {
        try {
            Multipart multipart = new MimeMultipart(ALTERNATIVE);
            addTextPart(multipart, htmlText, "text/html");
            addTextPart(multipart, retrievePlainTextMessage(plainText, htmlText), ContentTypeField.TYPE_TEXT_PLAIN);
            return multipart;
        } catch (IOException e) {
            throw new MessagingException("Cannot read specified content", e);
        }
    }

    private Multipart addTextPart(Multipart multipart, String text, String contentType) throws MessagingException, IOException {
        MimeBodyPart textReasonPart = new MimeBodyPart();
        textReasonPart.setDataHandler(
            new DataHandler(
                new ByteArrayDataSource(
                    text,
                    contentType + "; charset=UTF-8")));
        multipart.addBodyPart(textReasonPart);
        return multipart;
    }

    private String retrievePlainTextMessage(Optional<String> plainText, String htmlText) {
        return plainText.filter(text -> !text.isBlank()).orElseGet(() -> htmlTextExtractor.toPlainText(htmlText));
    }

}
