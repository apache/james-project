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

import com.google.common.base.Preconditions;
import org.apache.james.mailbox.store.extractor.TextExtractor;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.message.DefaultBodyDescriptorBuilder;
import org.apache.james.mime4j.message.MaximalBodyDescriptor;
import org.apache.james.mime4j.stream.EntityState;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.stream.MimeTokenStream;

import java.io.IOException;
import java.util.Deque;
import java.util.LinkedList;

public class MimePartParser {

    private final Message<? extends MailboxId> message;
    private final TextExtractor textExtractor;
    private final MimeTokenStream stream;
    private final Deque<MimePartContainerBuilder> builderStack;
    private MimePart result;
    private MimePartContainerBuilder currentlyBuildMimePart;

    public MimePartParser(Message<? extends MailboxId> message, TextExtractor textExtractor) {
        this.message = message;
        this.textExtractor = textExtractor;
        this.builderStack = new LinkedList<>();
        this.currentlyBuildMimePart = new RootMimePartContainerBuilder();
        this.stream = new MimeTokenStream(
            MimeConfig.custom().setMaxLineLen(-1).setMaxHeaderLen(-1).build(),
            new DefaultBodyDescriptorBuilder());
    }

    public MimePart parse() throws IOException, MimeException {
        stream.parse(message.getFullContent());
        for (EntityState state = stream.getState(); state != EntityState.T_END_OF_STREAM; state = stream.next()) {
            processMimePart(stream, state);
        }
        return result;
    }

    private void processMimePart(MimeTokenStream stream, EntityState state) throws IOException {
        switch (state) {
            case T_START_MULTIPART:
            case T_START_MESSAGE:
                stackCurrent();
                break;
            case T_START_HEADER:
                currentlyBuildMimePart = MimePart.builder();
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
        MimePart bodyMimePart = currentlyBuildMimePart.using(textExtractor).build();
        if (!builderStack.isEmpty()) {
            builderStack.peek().addChild(bodyMimePart);
        } else {
            Preconditions.checkState(result == null);
            result = bodyMimePart;
        }
    }

    private void manageBodyExtraction(MimeTokenStream stream) throws IOException {
        extractMimePartBodyDescription(stream);
        currentlyBuildMimePart.addBodyContent(stream.getDecodedInputStream());
    }

    private void extractMimePartBodyDescription(MimeTokenStream stream) {
        final MaximalBodyDescriptor descriptor = (MaximalBodyDescriptor) stream.getBodyDescriptor();
        currentlyBuildMimePart.addMediaType(descriptor.getMediaType())
            .addSubType(descriptor.getSubType())
            .addContentDisposition(descriptor.getContentDispositionType())
            .addFileName(descriptor.getContentDispositionFilename());
    }

}
