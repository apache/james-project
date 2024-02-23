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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Optional;

import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.model.ContentType.MediaType;
import org.apache.james.mailbox.model.ContentType.SubType;
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.field.LenientFieldParser;
import org.apache.james.mime4j.message.DefaultBodyDescriptorBuilder;
import org.apache.james.mime4j.message.MaximalBodyDescriptor;
import org.apache.james.mime4j.stream.EntityState;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.stream.MimeTokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public class MimePartParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(MimePartParser.class);
    private static final LenientFieldParser FIELD_PARSER = new LenientFieldParser();

    private final TextExtractor textExtractor;
    private final MimeTokenStream stream;
    private final Deque<MimePartContainerBuilder> builderStack;
    private MimePart.ParsedMimePart result;
    private MimePartContainerBuilder currentlyBuildMimePart;

    public MimePartParser(TextExtractor textExtractor) {
        this.textExtractor = textExtractor;
        this.builderStack = new LinkedList<>();
        this.currentlyBuildMimePart = new RootMimePartContainerBuilder();
        this.stream = new MimeTokenStream(
            MimeConfig.PERMISSIVE,
            new DefaultBodyDescriptorBuilder(null, FIELD_PARSER, DecodeMonitor.SILENT));
    }

    public MimePart.ParsedMimePart parse(InputStream inputStream) throws IOException, MimeException {
        stream.parse(inputStream);
        for (EntityState state = stream.getState(); state != EntityState.T_END_OF_STREAM; state = stream.next()) {
            processMimePart(stream, state);
        }
        return result;
    }

    private void processMimePart(MimeTokenStream stream, EntityState state) {
        switch (state) {
            case T_START_MULTIPART:
            case T_START_MESSAGE:
                stackCurrent();
                break;
            case T_START_HEADER:
                currentlyBuildMimePart = MimePart.builder(textExtractor::applicable);
                break;
            case T_FIELD:
                currentlyBuildMimePart.addToHeaders(stream.getField());
                break;
            case T_BODY:
                manageBodyExtraction(stream);
                closeMimePart();
                break;
            case T_END_MULTIPART:
            case T_END_MESSAGE:
                unstackToCurrent();
                closeMimePart();
                break;
            default:
                break;
        }
    }

    private void stackCurrent() {
        builderStack.push(currentlyBuildMimePart);
        currentlyBuildMimePart = null;
    }

    private void unstackToCurrent() {
        currentlyBuildMimePart = builderStack.pop();
    }
    
    private void closeMimePart() {
        MimePart.ParsedMimePart bodyMimePart = currentlyBuildMimePart.build();
        if (!builderStack.isEmpty()) {
            builderStack.peek().addChild(bodyMimePart);
        } else {
            Preconditions.checkState(result == null);
            result = bodyMimePart;
        }
    }

    private void manageBodyExtraction(MimeTokenStream stream) {
        extractMimePartBodyDescription(stream);
        currentlyBuildMimePart.addBodyContent(stream.getDecodedInputStream());
    }

    private void extractMimePartBodyDescription(MimeTokenStream stream) {
        try {
            MaximalBodyDescriptor descriptor = (MaximalBodyDescriptor) stream.getBodyDescriptor();

            Optional.ofNullable(descriptor.getMediaType())
                .map(MediaType::of)
                .ifPresent(currentlyBuildMimePart::addMediaType);
            Optional.ofNullable(descriptor.getSubType())
                .map(SubType::of)
                .ifPresent(currentlyBuildMimePart::addSubType);
            currentlyBuildMimePart.addContentDisposition(descriptor.getContentDispositionType())
                .addFileName(descriptor.getContentDispositionFilename());
            extractCharset(descriptor);
        } catch (Exception e) {
            LOGGER.warn("Failed to extract mime body part description", e);
        }
    }

    private void extractCharset(MaximalBodyDescriptor descriptor) {
        try {
            Optional.ofNullable(descriptor.getCharset())
                .map(Charset::forName)
                .ifPresent(currentlyBuildMimePart::charset);
        } catch (Exception e) {
            LOGGER.info("Failed parsing charset", e);
        }
    }
}
