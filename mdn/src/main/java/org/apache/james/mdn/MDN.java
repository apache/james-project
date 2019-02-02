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

package org.apache.james.mdn;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Properties;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.james.javax.MimeMultipartReport;
import org.apache.james.mime4j.Charsets;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.message.BasicBodyFactory;
import org.apache.james.mime4j.message.BodyPartBuilder;
import org.apache.james.mime4j.message.MultipartBuilder;
import org.apache.james.mime4j.stream.NameValuePair;

import com.google.common.base.Preconditions;

public class MDN {
    private static final NameValuePair UTF_8_CHARSET = new NameValuePair("charset", Charsets.UTF_8.name());

    public static final String DISPOSITION_CONTENT_TYPE = "message/disposition-notification";
    public static final String REPORT_SUB_TYPE = "report";
    public static final String DISPOSITION_NOTIFICATION_REPORT_TYPE = "disposition-notification";

    public static class Builder {
        private String humanReadableText;
        private MDNReport report;

        public Builder report(MDNReport report) {
            Preconditions.checkNotNull(report);
            this.report = report;
            return this;
        }

        public Builder humanReadableText(String humanReadableText) {
            Preconditions.checkNotNull(humanReadableText);
            this.humanReadableText = humanReadableText;
            return this;
        }

        public MDN build() {
            Preconditions.checkState(report != null);
            Preconditions.checkState(humanReadableText != null);
            Preconditions.checkState(!humanReadableText.trim().isEmpty());

            return new MDN(humanReadableText, report);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final String humanReadableText;
    private final MDNReport report;

    private MDN(String humanReadableText, MDNReport report) {
        this.humanReadableText = humanReadableText;
        this.report = report;
    }

    public String getHumanReadableText() {
        return humanReadableText;
    }

    public MDNReport getReport() {
        return report;
    }

    public MimeMultipart asMultipart() throws MessagingException {
        MimeMultipartReport multipart = new MimeMultipartReport();
        multipart.setSubType(REPORT_SUB_TYPE);
        multipart.setReportType(DISPOSITION_NOTIFICATION_REPORT_TYPE);
        multipart.addBodyPart(computeHumanReadablePart());
        multipart.addBodyPart(computeReportPart());
        // The optional third part, the original message is omitted.
        // We don't want to propogate over-sized, virus infected or
        // other undesirable mail!
        // There is the option of adding a Text/RFC822-Headers part, which
        // includes only the RFC 822 headers of the failed message. This is
        // described in RFC 1892. It would be a useful addition!
        return multipart;
    }

    public MimeMessage asMimeMessage() throws MessagingException {
        MimeMessage mimeMessage = new MimeMessage(Session.getDefaultInstance(new Properties()));
        mimeMessage.setContent(asMultipart());
        return mimeMessage;
    }

    public BodyPart computeHumanReadablePart() throws MessagingException {
        MimeBodyPart textPart = new MimeBodyPart();
        textPart.setText(humanReadableText, StandardCharsets.UTF_8.displayName());
        textPart.setDisposition(MimeMessage.INLINE);
        return textPart;
    }

    public BodyPart computeReportPart() throws MessagingException {
        MimeBodyPart mdnPart = new MimeBodyPart();
        mdnPart.setContent(report.formattedValue(), DISPOSITION_CONTENT_TYPE);
        return mdnPart;
    }

    public Message.Builder asMime4JMessageBuilder() throws IOException {
        Message.Builder messageBuilder = Message.Builder.of();
        messageBuilder.setBody(asMime4JMultipart());
        return messageBuilder;
    }

    private Multipart asMime4JMultipart() throws IOException {
        MultipartBuilder builder = MultipartBuilder.create(REPORT_SUB_TYPE);
        builder.addContentTypeParameter(new NameValuePair("report-type", DISPOSITION_NOTIFICATION_REPORT_TYPE));
        builder.addBodyPart(BodyPartBuilder.create()
            .use(new BasicBodyFactory())
            .setBody(humanReadableText, Charsets.UTF_8)
            .setContentType("text/plain", UTF_8_CHARSET));
        builder.addBodyPart(BodyPartBuilder.create()
            .use(new BasicBodyFactory())
            .setBody(report.formattedValue(), Charsets.UTF_8)
            .setContentType(DISPOSITION_CONTENT_TYPE, UTF_8_CHARSET));

        return builder.build();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MDN) {
            MDN mdn = (MDN) o;

            return Objects.equals(this.humanReadableText, mdn.humanReadableText)
                && Objects.equals(this.report, mdn.report);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(humanReadableText, report);
    }
}
