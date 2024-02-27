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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import jakarta.activation.DataHandler;
import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;

import org.apache.commons.io.IOUtils;
import org.apache.james.javax.MimeMultipartReport;
import org.apache.james.mime4j.Charsets;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.SingleBody;
import org.apache.james.mime4j.message.BasicBodyFactory;
import org.apache.james.mime4j.message.BodyPartBuilder;
import org.apache.james.mime4j.message.DefaultMessageWriter;
import org.apache.james.mime4j.message.MultipartBuilder;
import org.apache.james.mime4j.stream.NameValuePair;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

import scala.util.Try;

public class MDN {
    private static final NameValuePair UTF_8_CHARSET = new NameValuePair("charset", Charsets.UTF_8.name());

    public static final String DISPOSITION_CONTENT_TYPE = "message/disposition-notification";
    public static final String REPORT_SUB_TYPE = "report";
    public static final String DISPOSITION_NOTIFICATION_REPORT_TYPE = "disposition-notification";

    public static class Builder {
        private String humanReadableText;
        private MDNReport report;
        private Optional<Message> message = Optional.empty();

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

        public Builder message(Optional<Message> message) {
            this.message = message;
            return this;
        }

        public MDN build() {
            Preconditions.checkState(report != null);
            Preconditions.checkState(humanReadableText != null);

            return new MDN(humanReadableText, report, message);
        }
    }

    public static class MDNParseException extends Exception {
        public MDNParseException(String message) {
            super(message);
        }

        public MDNParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class MDNParseContentTypeException extends MDNParseException {
        public MDNParseContentTypeException(String message) {
            super(message);
        }
    }

    public static class MDNParseBodyPartInvalidException extends MDNParseException {

        public MDNParseBodyPartInvalidException(String message) {
            super(message);
        }
    }


    public static Builder builder() {
        return new Builder();
    }

    public static MDN parse(Message message) throws MDNParseException {
        if (!message.isMultipart()) {
            throw new MDNParseContentTypeException("MDN Message must be multipart");
        }
        List<Entity> bodyParts = ((Multipart) message.getBody()).getBodyParts();
        if (bodyParts.size() < 2) {
            throw new MDNParseBodyPartInvalidException("MDN Message must contain at least two parts");
        }
        try {
            return extractMDNReport(bodyParts)
                .map(Throwing.function(report -> {
                    String humanReadableText = extractHumanReadableText(bodyParts)
                        .orElse("");
                    return MDN.builder()
                        .humanReadableText(humanReadableText)
                        .report(report)
                        .message(extractOriginalMessage(bodyParts))
                        .build();
                }))
                .orElseThrow(() -> new MDNParseException("MDN can not extract. Report body part is invalid"));
        } catch (MDNParseException e) {
            throw e;
        } catch (Exception e) {
            throw new MDNParseException(e.getMessage(), e);
        }
    }

    private static Optional<Message> extractOriginalMessage(List<Entity> bodyParts) {
        if (bodyParts.size() < 3) {
            return Optional.empty();
        }
        Entity originalMessagePart = bodyParts.get(2);
        return Optional.of(originalMessagePart.getBody())
            .filter(Message.class::isInstance)
            .map(Message.class::cast);
    }

    public static Optional<String> extractHumanReadableText(List<Entity> entities) throws IOException {
        return entities.stream()
            .filter(entity -> entity.getMimeType().equals("text/plain"))
            .findAny()
            .map(Throwing.<Entity, String>function(entity -> {
                try (InputStream inputStream = ((SingleBody) entity.getBody()).getInputStream()) {
                    return IOUtils.toString(inputStream, entity.getCharset());
                }
            }).sneakyThrow());
    }

    public static Optional<MDNReport> extractMDNReport(List<Entity> entities) {
        return entities.stream()
            .filter(entity -> entity.getMimeType().startsWith(DISPOSITION_CONTENT_TYPE))
            .findAny()
            .flatMap(entity -> {
                try (InputStream inputStream = ((SingleBody) entity.getBody()).getInputStream()) {
                    Try<MDNReport> result = MDNReportParser.parse(inputStream, entity.getCharset());
                    if (result.isSuccess()) {
                        return Optional.of(result.get());
                    } else {
                        return Optional.empty();
                    }
                } catch (IOException e) {
                    return Optional.empty();
                }
            });
    }

    public boolean isReport(Entity entity) {
        return entity.getMimeType().startsWith(DISPOSITION_CONTENT_TYPE);
    }

    private final String humanReadableText;
    private final MDNReport report;
    private final Optional<Message> message;

    private MDN(String humanReadableText, MDNReport report, Optional<Message> message) {
        this.humanReadableText = humanReadableText;
        this.report = report;
        this.message = message;
    }

    public String getHumanReadableText() {
        return humanReadableText;
    }

    public MDNReport getReport() {
        return report;
    }

    public Optional<Message> getOriginalMessage() {
        return message;
    }

    public MimeMultipart asMultipart() throws MessagingException {
        MimeMultipartReport multipart = new MimeMultipartReport();
        multipart.setSubType(REPORT_SUB_TYPE);
        multipart.setReportType(DISPOSITION_NOTIFICATION_REPORT_TYPE);
        multipart.addBodyPart(computeHumanReadablePart());
        multipart.addBodyPart(computeReportPart());
        message.ifPresent(Throwing.consumer(originalMessage -> multipart.addBodyPart(computeOriginalMessagePart((Message) originalMessage)))
                .sneakyThrow());

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

    public BodyPart computeOriginalMessagePart(Message message) throws MessagingException {
        MimeBodyPart originalMessagePart = new MimeBodyPart();
        try {
            ByteArrayDataSource source = new ByteArrayDataSource(DefaultMessageWriter.asBytes(message), "message/rfc822");
            originalMessagePart.setDataHandler(new DataHandler(source));
            return originalMessagePart;
        } catch (IOException e) {
            throw new MessagingException("Could not write message as bytes", e);
        }
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
                && Objects.equals(this.report, mdn.report)
                && Objects.equals(this.message, mdn.message);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(humanReadableText, report, message);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("humanReadableText", humanReadableText)
            .add("report", report)
            .add("message", message)
            .toString();
    }
}
