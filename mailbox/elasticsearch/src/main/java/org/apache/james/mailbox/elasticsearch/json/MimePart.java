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

package org.apache.james.mailbox.elasticsearch.json;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.extractor.ParsedContent;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.model.ContentType;
import org.apache.james.mailbox.model.ContentType.MediaType;
import org.apache.james.mailbox.model.ContentType.SubType;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mime4j.stream.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

public class MimePart {

    public static class Builder implements MimePartContainerBuilder {

        private final HeaderCollection.Builder headerCollectionBuilder;
        private Optional<InputStream> bodyContent;
        private final List<MimePart> children;
        private Optional<MediaType> mediaType;
        private Optional<SubType> subType;
        private Optional<String> fileName;
        private Optional<String> fileExtension;
        private Optional<String> contentDisposition;
        private Optional<Charset> charset;
        private TextExtractor textExtractor;

        private Builder() {
            children = Lists.newArrayList();
            headerCollectionBuilder = HeaderCollection.builder();
            this.bodyContent = Optional.empty();
            this.mediaType = Optional.empty();
            this.subType = Optional.empty();
            this.fileName = Optional.empty();
            this.fileExtension = Optional.empty();
            this.contentDisposition = Optional.empty();
            this.charset = Optional.empty();
            this.textExtractor = new DefaultTextExtractor();
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
        public Builder addChild(MimePart mimePart) {
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
        public MimePartContainerBuilder using(TextExtractor textExtractor) {
            Preconditions.checkArgument(textExtractor != null, "Provided text extractor should not be null");
            this.textExtractor = textExtractor;
            return this;
        }

        @Override
        public MimePartContainerBuilder charset(Charset charset) {
            this.charset = Optional.of(charset);
            return this;
        }

        @Override
        public MimePart build() {
            Optional<ParsedContent> parsedContent = parseContent(textExtractor);
            return new MimePart(
                headerCollectionBuilder.build(),
                parsedContent.flatMap(ParsedContent::getTextualContent),
                mediaType,
                subType,
                fileName,
                fileExtension,
                contentDisposition,
                children);
        }

        private Optional<ParsedContent> parseContent(TextExtractor textExtractor) {
            if (bodyContent.isPresent()) {
                try {
                    return Optional.of(extractText(textExtractor, bodyContent.get()));
                } catch (Throwable e) {
                    LOGGER.warn("Failed parsing attachment", e);
                }
            }
            return Optional.empty();
        }

        private ParsedContent extractText(TextExtractor textExtractor, InputStream bodyContent) throws Exception {
            if (shouldPerformTextExtraction()) {
                return textExtractor.extractContent(
                    bodyContent,
                    computeContentType().orElse(null));
            }
            return new ParsedContent(
                Optional.ofNullable(IOUtils.toString(bodyContent, charset.orElse(StandardCharsets.UTF_8))),
                ImmutableMap.of());
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

        private Optional<ContentType> computeContentType() {
            if (mediaType.isPresent() && subType.isPresent()) {
                return Optional.of(ContentType.of(
                    ContentType.MimeType.of(mediaType.get(), subType.get()),
                    charset));
            } else {
                return Optional.empty();
            }
        }

    }
    
    public static Builder builder() {
        return new Builder();
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
        return firstBody(textAttachments()
                .filter(this::isHtmlSubType));
    }

    private Optional<String> firstBody(Stream<MimePart> mimeParts) {
        return mimeParts
                .map((mimePart) -> mimePart.bodyTextContent)
                .filter(Optional::isPresent)
                .map(Optional::get)
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
                .flatMap((mimePart) -> Stream.concat(Stream.of(mimePart), mimePart.getAttachmentsStream()));
    }

}
