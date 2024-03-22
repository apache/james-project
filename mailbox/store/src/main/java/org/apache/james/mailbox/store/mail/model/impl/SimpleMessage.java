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
package org.apache.james.mailbox.store.mail.model.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.input.BoundedInputStream;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.mail.model.Message;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Flux;

public class SimpleMessage implements Message {

    private final MessageId messageId;
    private final Content content;
    private final int bodyStartOctet;
    private final Date internalDate;
    private final long size;
    private final Long textualLineCount;
    private final Properties properties;
    private final List<MessageAttachmentMetadata> attachments;

    public SimpleMessage(MessageId messageId, Content content, long size, Date internalDate, int bodyStartOctet, Long textualLineCount, Properties properties, List<MessageAttachmentMetadata> attachments) {
        this.messageId = messageId;
        this.content = content;
        this.bodyStartOctet = bodyStartOctet;
        this.internalDate = internalDate;
        this.size = size;
        this.textualLineCount = textualLineCount;
        this.properties = properties;
        this.attachments = attachments;
    }

    @Override
    public MessageId getMessageId() {
        return messageId;
    }

    @Override
    public Date getInternalDate() {
        return internalDate;
    }

    @Override
    public InputStream getBodyContent() throws IOException {
        InputStream inputStream = content.getInputStream();
        inputStream.skip(bodyStartOctet);
        return inputStream;
    }

    @Override
    public String getMediaType() {
        return properties.getMediaType();
    }

    @Override
    public String getSubType() {
        return properties.getSubType();
    }

    @Override
    public long getBodyOctets() {
        return getFullContentOctets() - bodyStartOctet;
    }

    @Override
    public long getHeaderOctets() {
        return bodyStartOctet;
    }

    @Override
    public long getFullContentOctets() {
        return size;
    }

    @Override
    public Long getTextualLineCount() {
        return textualLineCount;
    }

    @Override
    public InputStream getHeaderContent() throws IOException {
        long headerEnd = bodyStartOctet;
        if (headerEnd < 0) {
            headerEnd = 0;
        }
        return new BoundedInputStream(content.getInputStream(), headerEnd);
    }

    @Override
    public InputStream getFullContent() throws IOException {
        return content.getInputStream();
    }

    @Override
    public Optional<byte[][]> getFullBytes() {
        return content.asBytesSequence();
    }

    @Override
    public Properties getProperties() {
        return properties;
    }

    @Override
    public List<MessageAttachmentMetadata> getAttachments() {
        return attachments;
    }

    @Override
    public Publisher<ByteBuffer> getHeaderContentReactive() {
        try {
            if (bodyStartOctet >= content.size()) {
                return content.reactiveBytes();
            }
        } catch (MailboxException e) {
            return Flux.error(e);
        }
        return Message.super.getHeaderContentReactive();
    }

    @Override
    public Publisher<ByteBuffer> getFullContentReactive() {
        return content.reactiveBytes();
    }
}
