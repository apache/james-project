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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import org.apache.commons.io.FilenameUtils;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.extractor.ParsedContent;
import org.apache.james.mailbox.store.extractor.TextExtractor;
import org.apache.james.mime4j.stream.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class MimePart {

    public static class Builder implements MimePartContainerBuilder {

        private HeaderCollection.Builder headerCollectionBuilder;
        private Optional<InputStream> bodyContent;
        private List<MimePart> children;
        private Optional<String> mediaType;
        private Optional<String> subType;
        private Optional<String> fileName;
        private Optional<String> fileExtension;
        private Optional<String> contentDisposition;
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
        public Builder addMediaType(String mediaType) {
            this.mediaType = Optional.ofNullable(mediaType);
            return this;
        }

        @Override
        public Builder addSubType(String subType) {
            this.subType = Optional.ofNullable(subType);
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
        public MimePart build() {
            Optional<ParsedContent> parsedContent = parseContent(textExtractor);
            return new MimePart(
                headerCollectionBuilder.build(),
                parsedContent.map( x -> Optional.ofNullable(x.getTextualContent()))
                    .orElse(Optional.empty())
                ,
                mediaType,
                subType,
                fileName,
                fileExtension,
                contentDisposition,
                children,
                parsedContent
                    .map(x -> x.getMetadata()
                        .entrySet()
                        .stream()
                        .reduce(ImmutableMultimap.<String, String>builder(),
                            (builder, entry) -> builder.putAll(entry.getKey(), entry.getValue()),
                            (builder1, builder2) -> builder1.putAll(builder2.build())).build())
                    .orElse(ImmutableMultimap.of())
            );
        }

        private Optional<ParsedContent> parseContent(TextExtractor textExtractor) {
            if (bodyContent.isPresent()) {
                try {
                    return Optional.of(textExtractor.extractContent(
                        bodyContent.get(),
                        computeContentType().orElse(null),
                        fileName.orElse(null)));
                } catch (Exception e) {
                    LOGGER.warn("Failed parsing attachment", e);
                }
            }
            return Optional.empty();
        }

        private Optional<String> computeContentType() {
            if (mediaType.isPresent() && subType.isPresent()) {
                return Optional.of(mediaType.get() + "/" + subType.get());
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
    private final Optional<String> mediaType;
    private final Optional<String> subType;
    private final Optional<String> fileName;
    private final Optional<String> fileExtension;
    private final Optional<String> contentDisposition;
    private final List<MimePart> attachments;
    private final ImmutableMultimap<String, String> metadata;

    private MimePart(HeaderCollection headerCollection, Optional<String> bodyTextContent, Optional<String> mediaType,
                    Optional<String> subType, Optional<String> fileName, Optional<String> fileExtension,
                    Optional<String> contentDisposition, List<MimePart> attachments, Multimap<String, String> metadata) {
        this.headerCollection = headerCollection;
        this.mediaType = mediaType;
        this.subType = subType;
        this.fileName = fileName;
        this.fileExtension = fileExtension;
        this.contentDisposition = contentDisposition;
        this.attachments = attachments;
        this.bodyTextContent = bodyTextContent;
        this.metadata = ImmutableMultimap.copyOf(metadata);
    }

    @JsonIgnore
    public List<MimePart> getAttachments() {
        return attachments;
    }

    @JsonIgnore
    public HeaderCollection getHeaderCollection() {
        return headerCollection;
    }

    @JsonProperty(JsonMessageConstants.HEADERS)
    public Multimap<String, String> getHeaders() {
        return headerCollection.getHeaders();
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
        return mediaType;
    }

    @JsonProperty(JsonMessageConstants.Attachment.SUBTYPE)
    public Optional<String> getSubType() {
        return subType;
    }

    @JsonProperty(JsonMessageConstants.Attachment.CONTENT_DISPOSITION)
    public Optional<String> getContentDisposition() {
        return contentDisposition;
    }

    @JsonProperty(JsonMessageConstants.Attachment.TEXT_CONTENT)
    public Optional<String> getTextualBody() {
        return bodyTextContent;
    }

    @JsonProperty(JsonMessageConstants.Attachment.FILE_METADATA)
    public ImmutableMultimap<String, String> getMetadata() {
        return metadata;
    }

    @JsonIgnore
    public Optional<String> locateFirstTextualBody() {
        return Stream.concat(
                    Stream.of(this),
                    attachments.stream())
                .map((mimePart) -> mimePart.bodyTextContent)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    @JsonIgnore
    public Stream<MimePart> getAttachmentsStream() {
        return attachments.stream()
                .flatMap((mimePart) -> Stream.concat(Stream.of(mimePart), mimePart.getAttachmentsStream()));
    }

}
