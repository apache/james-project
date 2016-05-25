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
import java.util.Date;
import java.util.List;

import javax.mail.internet.SharedInputStream;

import org.apache.james.mailbox.store.mail.model.AttachmentId;
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mailbox.store.mail.model.MessageId;
import org.apache.james.mailbox.store.mail.model.Property;

import com.google.common.collect.ImmutableList;

public class SimpleMessage implements Message {

    private final String subType;
    private final String mediaType;
    private final SharedInputStream content;
    private final int bodyStartOctet;
    private final Date internalDate;
    private final long size;
    private final Long textualLineCount;
    private final List<Property> properties;
    private final List<AttachmentId> attachmentsIds;

    public SimpleMessage(SharedInputStream content, long size, Date internalDate, String subType, String mediaType, int bodyStartOctet, Long textualLineCount, List<Property> properties, List<AttachmentId> attachmentsIds) {
        this.subType = subType;
        this.mediaType = mediaType;
        this.content = content;
        this.bodyStartOctet = bodyStartOctet;
        this.internalDate = internalDate;
        this.size = size;
        this.textualLineCount = textualLineCount;
        this.properties = properties;
        this.attachmentsIds = attachmentsIds;
    }

    public SimpleMessage(SharedInputStream content, long size, Date internalDate, String subType, String mediaType, int bodyStartOctet, Long textualLineCount, List<Property> properties) {
        this(content, size, internalDate, subType, mediaType, bodyStartOctet, textualLineCount, properties, ImmutableList.<AttachmentId>of());
    }

    @Override
    public MessageId getMessageId() {
        return null;
    }

    @Override
    public Date getInternalDate() {
        return internalDate;
    }

    @Override
    public InputStream getBodyContent() throws IOException {
        return content.newStream(bodyStartOctet, -1);
    }

    @Override
    public String getMediaType() {
        return mediaType;
    }

    @Override
    public String getSubType() {
        return subType;
    }

    @Override
    public long getBodyOctets() {
        return getFullContentOctets() - bodyStartOctet;
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
        return content.newStream(0, headerEnd);
    }

    @Override
    public InputStream getFullContent() throws IOException {
        return content.newStream(0, -1);
    }

    @Override
    public List<Property> getProperties() {
        return properties;
    }

    @Override
    public List<AttachmentId> getAttachmentsIds() {
        return attachmentsIds;
    }
}
