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
package org.apache.james.mailbox.jpa.mail.model.openjpa;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import javax.mail.Flags;

import org.apache.commons.io.input.BoundedInputStream;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.mail.model.JPAMailbox;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.openjpa.persistence.Persistent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;

/**
 * JPA implementation of {@link AbstractJPAMailboxMessage} which use openjpas {@link Persistent} type to
 * be able to stream the message content without loading it into the memory at all. 
 * 
 * This is not supported for all DB's yet. See <a href="http://openjpa.apache.org/builds/latest/docs/manual/ref_guide_mapping_jpa.html">Additional JPA Mappings</a>
 * 
 * If your DB is not supported by this, use {@link JPAMailboxMessage}
 *
 * TODO: Fix me!
 */
@Entity(name = "MailboxMessage")
@Table(name = "JAMES_MAIL")
public class JPAStreamingMailboxMessage extends AbstractJPAMailboxMessage {

    @Persistent(optional = false, fetch = FetchType.LAZY)
    @Column(name = "MAIL_BYTES", length = 1048576000, nullable = false)
    private InputStream body;

    @Persistent(optional = false, fetch = FetchType.LAZY)
    @Column(name = "HEADER_BYTES", length = 10485760, nullable = false)
    private InputStream header;

    private final Content content;

    public JPAStreamingMailboxMessage(JPAMailbox mailbox, Date internalDate, int size, Flags flags, Content content, int bodyStartOctet, PropertyBuilder propertyBuilder) throws MailboxException {
        super(mailbox, internalDate, flags, size, bodyStartOctet, propertyBuilder);
        this.content = content;

        try {
            this.header = new BoundedInputStream(content.getInputStream(), getBodyStartOctet());
            InputStream bodyStream = content.getInputStream();
            bodyStream.skip(getBodyStartOctet());
            this.body = bodyStream;

        } catch (IOException e) {
            throw new MailboxException("Unable to parse message",e);
        }
    }

    /**
     * Create a copy of the given message
     */
    public JPAStreamingMailboxMessage(JPAMailbox mailbox, MessageUid uid, ModSeq modSeq, MailboxMessage message) throws MailboxException {
        super(mailbox, uid, modSeq, message);
        this.content = new Content() {
            @Override
            public InputStream getInputStream() throws IOException {
                return message.getFullContent();
            }

            @Override
            public long size() {
                return message.getFullContentOctets();
            }
        };
        try {
            this.header = getHeaderContent();
            this.body = getBodyContent();
        } catch (IOException e) {
            throw new MailboxException("Unable to parse message",e);
        }
    }

    @Override
    public InputStream getBodyContent() throws IOException {
        InputStream inputStream = content.getInputStream();
        inputStream.skip(getBodyStartOctet());
        return inputStream;
    }

    @Override
    public InputStream getHeaderContent() throws IOException {
        int headerEnd = getBodyStartOctet() - 2;
        if (headerEnd < 0) {
            headerEnd = 0;
        }
        return new BoundedInputStream(content.getInputStream(), headerEnd);
    }

    @Override
    public MailboxMessage copy(Mailbox mailbox) throws MailboxException {
        return new JPAStreamingMailboxMessage(JPAMailbox.from(mailbox), getUid(), getModSeq(), this);
    }
}
