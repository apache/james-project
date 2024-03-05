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

package org.apache.james.util.mime;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import jakarta.mail.internet.MimeMessage;

import org.apache.commons.io.IOUtils;
import org.apache.james.mime4j.dom.Body;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.util.html.HtmlTextExtractor;

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.functions.ThrowingFunction;
import com.google.common.base.Strings;

public class MessageContentExtractor {

    public static final String CONTENT_ID = "Content-ID";
    public static final String MULTIPART_ALTERNATIVE = "multipart/alternative";
    public static final String TEXT_HTML = "text/html";
    public static final String TEXT_PLAIN = "text/plain";

    public MessageContent extract(org.apache.james.mime4j.dom.Message message) throws IOException {
        Body body = message.getBody();
        if (body instanceof TextBody) {
            return parseTextBody(message, (TextBody)body);
        }
        if (body instanceof Multipart) {
            return parseMultipart(message, (Multipart)body);
        }
        return MessageContent.empty();
    }

    private MessageContent parseTextBody(Entity entity, TextBody textBody) throws IOException {
        Optional<String> bodyContent = asString(textBody);
        if (TEXT_HTML.equals(entity.getMimeType())) {
            return MessageContent.ofHtmlOnly(bodyContent);
        }
        return MessageContent.ofTextOnly(bodyContent);
    }

    private MessageContent parseMultipart(Entity entity, Multipart multipart) throws IOException {
        MessageContent messageContent = parseMultipartContent(entity, multipart);
        if (!messageContent.isEmpty()) {
            return messageContent;
        }
        return parseFirstFoundMultipart(multipart);
    }

    private MessageContent parseMultipartContent(Entity entity, Multipart multipart) throws IOException {
        switch (entity.getMimeType()) {
        case MULTIPART_ALTERNATIVE:
            return retrieveHtmlAndPlainTextContent(multipart);
        default:
            return retrieveFirstReadablePart(multipart);
        }
    }

    private MessageContent parseFirstFoundMultipart(Multipart multipart) throws IOException {
        ThrowingFunction<Entity, MessageContent> parseMultipart = firstPart -> parseMultipart(firstPart, (Multipart)firstPart.getBody());
        return multipart.getBodyParts()
            .stream()
            .filter(part -> part.getBody() instanceof Multipart)
            .findFirst()
            .map(Throwing.function(parseMultipart).sneakyThrow())
            .orElse(MessageContent.empty());
    }

    private Optional<String> asString(TextBody textBody) throws IOException {
        return Optional.ofNullable(new String(IOUtils.toByteArray(textBody.getInputStream(), textBody.size()),
            Optional.ofNullable(textBody.getCharset())
                .orElse(org.apache.james.mime4j.Charsets.DEFAULT_CHARSET)));
    }

    private MessageContent retrieveHtmlAndPlainTextContent(Multipart multipart) throws IOException {
        Optional<String> textBody = getFirstMatchingTextBody(multipart, TEXT_PLAIN);
        Optional<String> htmlBody = getFirstMatchingTextBody(multipart, TEXT_HTML);
        MessageContent directChildTextBodies = new MessageContent(textBody, htmlBody);
        if (!directChildTextBodies.isComplete()) {
            MessageContent fromInnerMultipart = parseFirstFoundMultipart(multipart);
            return directChildTextBodies.merge(fromInnerMultipart);
        }
        return directChildTextBodies;
    }

    private MessageContent retrieveFirstReadablePart(Multipart multipart) throws IOException {
        return retrieveFirstReadablePartMatching(multipart, this::isNotAttachment)
            .orElseGet(() -> retrieveFirstReadablePartMatching(multipart, this::isInlinedWithoutCid)
                .orElse(MessageContent.empty()));
    }

    private Optional<MessageContent> retrieveFirstReadablePartMatching(Multipart multipart, Predicate<Entity> predicate) {
        return multipart.getBodyParts()
            .stream()
            .filter(predicate)
            .flatMap(Throwing.function(this::extractContentIfReadable).sneakyThrow())
            .findFirst();
    }

    private Stream<MessageContent> extractContentIfReadable(Entity entity) throws IOException {
        if (TEXT_HTML.equals(entity.getMimeType()) && entity.getBody() instanceof TextBody) {
            return Stream.of(
                    MessageContent.ofHtmlOnly(asString((TextBody)entity.getBody())));
        }
        if (TEXT_PLAIN.equals(entity.getMimeType()) && entity.getBody() instanceof TextBody) {
            return Stream.of(
                    MessageContent.ofTextOnly(asString((TextBody)entity.getBody())));
        }
        if (entity.isMultipart() && entity.getBody() instanceof Multipart) {
            MessageContent innerMultipartContent = parseMultipart(entity, (Multipart)entity.getBody());
            if (!innerMultipartContent.isEmpty()) {
                return Stream.of(innerMultipartContent);
            }
        }
        return Stream.empty();
    }

    private Optional<String> getFirstMatchingTextBody(Multipart multipart, String mimeType) throws IOException {
        Optional<String> firstMatchingTextBody = getFirstMatchingTextBody(multipart, mimeType, this::isNotAttachment);
        if (firstMatchingTextBody.isPresent()) {
            return firstMatchingTextBody;
        }
        Optional<String> fallBackInlinedBodyWithoutCid = getFirstMatchingTextBody(multipart, mimeType, this::isInlinedWithoutCid);
        return fallBackInlinedBodyWithoutCid;
    }

    private Optional<String> getFirstMatchingTextBody(Multipart multipart, String mimeType, Predicate<Entity> condition) {
        Function<TextBody, Optional<String>> textBodyOptionalFunction = Throwing
            .function(this::asString).sneakyThrow();

        return multipart.getBodyParts()
            .stream()
            .filter(part -> mimeType.equals(part.getMimeType()))
            .filter(condition)
            .map(Entity::getBody)
            .filter(TextBody.class::isInstance)
            .map(TextBody.class::cast)
            .findFirst()
            .flatMap(textBodyOptionalFunction);
    }

    private boolean isNotAttachment(Entity part) {
        return part.getDispositionType() == null;
    }

    private boolean isInlinedWithoutCid(Entity part) {
        return Objects.equals(part.getDispositionType(), MimeMessage.INLINE)
            && part.getHeader().getField(CONTENT_ID) == null;
    }

    public static final class MessageContent {
        private final Optional<String> textBody;
        private final Optional<String> htmlBody;

        public MessageContent(Optional<String> textBody, Optional<String> htmlBody) {
            this.textBody = textBody;
            this.htmlBody = htmlBody;
        }

        public static MessageContent ofTextOnly(Optional<String> textBody) {
            return new MessageContent(textBody, Optional.empty());
        }

        public static MessageContent ofHtmlOnly(Optional<String> htmlBody) {
            return new MessageContent(Optional.empty(), htmlBody);
        }

        public static MessageContent empty() {
            return new MessageContent(Optional.empty(), Optional.empty());
        }
        
        public Optional<String> getTextBody() {
            return textBody;
        }

        public Optional<String> getHtmlBody() {
            return htmlBody;
        }
        
        public boolean isEmpty() {
            return equals(empty());
        }

        public boolean isComplete() {
            return textBody.isPresent() && htmlBody.isPresent();
        }

        public MessageContent merge(MessageContent fromInnerMultipart) {
            return new MessageContent(
                    textBody.or(fromInnerMultipart::getTextBody),
                    htmlBody.or(fromInnerMultipart::getHtmlBody));
        }

        public Optional<String> extractMainTextContent(HtmlTextExtractor htmlTextExtractor) {
            return htmlBody.map(htmlTextExtractor::toPlainText)
                .filter(Predicate.not(Strings::isNullOrEmpty))
                .or(() -> textBody);
        }

        @Override
        public int hashCode() {
            return Objects.hash(textBody, htmlBody);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof MessageContent)) {
                return false;
            }
            MessageContent otherMessageContent = (MessageContent)other;
            return Objects.equals(this.textBody, otherMessageContent.textBody)
                    && Objects.equals(this.htmlBody, otherMessageContent.htmlBody);
        }
    }
}
