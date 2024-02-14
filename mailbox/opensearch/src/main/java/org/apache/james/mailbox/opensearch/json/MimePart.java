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

package org.apache.james.mailbox.opensearch.json;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.extractor.ParsedContent;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.model.ContentType;
import org.apache.james.mailbox.model.ContentType.MediaType;
import org.apache.james.mailbox.model.ContentType.SubType;
import org.apache.james.mime4j.stream.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.fge.lambdas.Throwing;
import com.google.common.collect.Lists;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MimePart {

    public static class Builder implements MimePartContainerBuilder {

        private final HeaderCollection.Builder headerCollectionBuilder;
        private Optional<InputStream> bodyContent;
        private final List<ParsedMimePart> children;
        private Optional<MediaType> mediaType;
        private Optional<SubType> subType;
        private Optional<String> fileName;
        private Optional<String> fileExtension;
        private Optional<String> contentDisposition;
        private Optional<Charset> charset;
        private Predicate<ContentType> shouldCaryOverContent;

        private Builder(Predicate<ContentType> shouldCaryOverContent) {
            this.shouldCaryOverContent = shouldCaryOverContent;
            children = Lists.newArrayList();
            headerCollectionBuilder = HeaderCollection.builder();
            this.bodyContent = Optional.empty();
            this.mediaType = Optional.empty();
            this.subType = Optional.empty();
            this.fileName = Optional.empty();
            this.fileExtension = Optional.empty();
            this.contentDisposition = Optional.empty();
            this.charset = Optional.empty();
        }

        @Override
        public Builder addToHeaders(Field field) {
            headerCollectionBuilder.add(field);
            return this;
        }

        @Override
        public Builder addBodyContent(InputStream bodyContent) {
            this.bodyContent = Optional.of(bodyContent);
            return this;
        }

        @Override
        public Builder addChild(ParsedMimePart mimePart) {
            children.add(mimePart);
            return this;
        }

        @Override
        public Builder addFileName(String fileName) {
            this.fileName = Optional.ofNullable(fileName);
            this.fileExtension = this.fileName.map(FilenameUtils::getExtension);
            return this;
        }

        @Override
        public Builder addMediaType(MediaType mediaType) {
            this.mediaType = Optional.ofNullable(mediaType);
            return this;
        }

        @Override
        public Builder addSubType(SubType subType) {
            this.subType = Optional.of(subType);
            return this;
        }

        @Override
        public Builder addContentDisposition(String contentDisposition) {
            this.contentDisposition = Optional.ofNullable(contentDisposition);
            return this;
        }

        @Override
        public MimePartContainerBuilder charset(Charset charset) {
            this.charset = Optional.of(charset);
            return this;
        }

        private Optional<ContentType> computeContentType() {
            if (mediaType.isPresent() && subType.isPresent()) {
                return Optional.of(ContentType.of(
                    ContentType.MimeType.of(mediaType.get(), subType.get()),
                    charset));
            } else {
                return Optional.empty();
            }
        }

        @Override
        public ParsedMimePart build() {
            final Optional<ContentType> contentType = computeContentType();
            return new ParsedMimePart(
                headerCollectionBuilder.build(),
                bodyContent.filter(any -> shouldCaryOverContent.test(contentType.orElse(null))),
                charset,
                mediaType,
                subType,
                contentType,
                fileName,
                fileExtension,
                contentDisposition,
                children);
        }
    }

    public static class ParsedMimePart {
        private final HeaderCollection headerCollection;
        private final Optional<byte[]> bodyContent;
        private final Optional<Charset> charset;
        private final Optional<MediaType> mediaType;
        private final Optional<SubType> subType;
        private Optional<ContentType> contentType;
        private final Optional<String> fileName;
        private final Optional<String> fileExtension;
        private final Optional<String> contentDisposition;
        private final List<ParsedMimePart> attachments;

        public ParsedMimePart(HeaderCollection headerCollection, Optional<InputStream> bodyContent, Optional<Charset> charset,
                              Optional<MediaType> mediaType,
                              Optional<SubType> subType, Optional<ContentType> contentType, Optional<String> fileName, Optional<String> fileExtension,
                              Optional<String> contentDisposition, List<ParsedMimePart> attachments) {
            this.headerCollection = headerCollection;
            this.mediaType = mediaType;
            this.subType = subType;
            this.contentType = contentType;
            this.fileName = fileName;
            this.fileExtension = fileExtension;
            this.contentDisposition = contentDisposition;
            this.attachments = attachments;
            this.charset = charset;

            this.bodyContent = bodyContent.map(Throwing.function(IOUtils::toByteArray));
        }

        public Mono<MimePart> asMimePart(TextExtractor textExtractor) {
            return Flux.fromIterable(attachments)
                .concatMap(attachment -> attachment.asMimePart(textExtractor))
                .collectList()
                .flatMap(attachments -> extractText(textExtractor)
                    .map(Optional::ofNullable)
                    .switchIfEmpty(Mono.just(Optional.empty()))
                    .onErrorResume(e -> {
                        LOGGER.warn("Failure extracting text message for some attachments", e);
                        return Mono.just(Optional.empty());
                    })
                    .map(text -> new MimePart(headerCollection, text.flatMap(ParsedContent::getTextualContent),
                        mediaType, subType, fileName, fileExtension, contentDisposition, attachments)));
        }

        private Mono<ParsedContent> extractText(TextExtractor textExtractor) {
            return Mono.justOrEmpty(bodyContent)
                .flatMap(content -> {
                    if (shouldPerformTextExtraction()) {
                        return textExtractor.extractContentReactive(
                                new ByteArrayInputStream(content),
                                contentType.orElse(null));
                    }
                    return Mono.fromCallable(() -> ParsedContent.of(new String(content, charset.orElse(StandardCharsets.UTF_8))));
                });
        }

        private boolean shouldPerformTextExtraction() {
            return !isTextBody() || isHtml();
        }

        private Boolean isTextBody() {
            return mediaType.map(MediaType.of("text")::equals).orElse(false);
        }

        private Boolean isHtml() {
            return isTextBody() && subType.map(SubType.of("html")::equals).orElse(false);
        }

    }

    public static Builder builder(Predicate<ContentType> shouldCaryOverContent) {
        return new Builder(shouldCaryOverContent);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(MimePart.class);

    private final HeaderCollection headerCollection;
    private final Optional<String> bodyTextContent;
    private final Optional<MediaType> mediaType;
    private final Optional<SubType> subType;
    private final Optional<String> fileName;
    private final Optional<String> fileExtension;
    private final Optional<String> contentDisposition;
    private final List<MimePart> attachments;

    private MimePart(HeaderCollection headerCollection, Optional<String> bodyTextContent, Optional<MediaType> mediaType,
                    Optional<SubType> subType, Optional<String> fileName, Optional<String> fileExtension,
                    Optional<String> contentDisposition, List<MimePart> attachments) {
        this.headerCollection = headerCollection;
        this.mediaType = mediaType;
        this.subType = subType;
        this.fileName = fileName;
        this.fileExtension = fileExtension;
        this.contentDisposition = contentDisposition;
        this.attachments = attachments;
        this.bodyTextContent = bodyTextContent;
    }

    @JsonIgnore
    public List<MimePart> getAttachments() {
        return attachments;
    }

    @JsonIgnore
    public HeaderCollection getHeaderCollection() {
        return headerCollection;
    }

    @JsonProperty(JsonMessageConstants.Attachment.FILENAME)
    public Optional<String> getFileName() {
        return fileName;
    }

    @JsonProperty(JsonMessageConstants.Attachment.FILE_EXTENSION)
    public Optional<String> getFileExtension() {
        return fileExtension;
    }

    @JsonProperty(JsonMessageConstants.Attachment.MEDIA_TYPE)
    public Optional<String> getMediaType() {
        return mediaType.map(MediaType::asString);
    }

    @JsonProperty(JsonMessageConstants.Attachment.SUBTYPE)
    public Optional<String> getSubType() {
        return subType.map(SubType::asString);
    }

    @JsonProperty(JsonMessageConstants.Attachment.CONTENT_DISPOSITION)
    public Optional<String> getContentDisposition() {
        return contentDisposition;
    }

    @JsonProperty(JsonMessageConstants.Attachment.TEXT_CONTENT)
    public Optional<String> getTextualBody() {
        return bodyTextContent;
    }

    @JsonIgnore
    public Optional<String> locateFirstTextBody() {
        return firstBody(textAttachments()
                .filter(this::isPlainSubType));
    }

    @JsonIgnore
    public Optional<String> locateFirstHtmlBody() {
        if (locateFirstTextBody().isEmpty()) {
            return firstBody(textAttachments()
                .filter(this::isHtmlSubType));
        }
        return Optional.empty();
    }

    private Optional<String> firstBody(Stream<MimePart> mimeParts) {
        return mimeParts
            .map(mimePart -> mimePart.bodyTextContent)
            .flatMap(Optional::stream)
            .findFirst();
    }

    private Stream<MimePart> textAttachments() {
        return Stream.concat(
                    Stream.of(this),
                    attachments.stream())
                .filter(this::isTextMediaType);
    }

    private boolean isTextMediaType(MimePart mimePart) {
        return mimePart.getMediaType()
                .filter("text"::equals)
                .isPresent();
    }

    private boolean isPlainSubType(MimePart mimePart) {
        return mimePart.getSubType()
                .filter("plain"::equals)
                .isPresent();
    }

    private boolean isHtmlSubType(MimePart mimePart) {
        return mimePart.getSubType()
                .filter("html"::equals)
                .isPresent();
    }

    @JsonIgnore
    public Stream<MimePart> getAttachmentsStream() {
        return attachments.stream()
                .flatMap(mimePart -> Stream.concat(Stream.of(mimePart), mimePart.getAttachmentsStream()))
                .filter(mimePart -> mimePart.contentDisposition.isPresent());
    }

}
