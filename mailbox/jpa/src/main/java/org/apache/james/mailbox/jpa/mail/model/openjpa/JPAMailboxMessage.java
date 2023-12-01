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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import javax.mail.Flags;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.mail.model.JPAMailbox;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;

import com.google.common.annotations.VisibleForTesting;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity(name = "MailboxMessage")
@Table(name = "JAMES_MAIL")
public class JPAMailboxMessage extends AbstractJPAMailboxMessage {

    private static final byte[] EMPTY_ARRAY = new byte[] {};

    /** The value for the body field. Lazy loaded */
    /** We use a max length to represent 1gb data. Thats prolly overkill, but who knows */
    @Basic(optional = false, fetch = FetchType.LAZY)
    @Column(name = "MAIL_BYTES", length = 1048576000, nullable = false)
    @Lob private byte[] body;


    /** The value for the header field. Lazy loaded */
    /** We use a max length to represent 10mb data. Thats prolly overkill, but who knows */
    @Basic(optional = false, fetch = FetchType.LAZY)
    @Column(name = "HEADER_BYTES", length = 10485760, nullable = false)
    @Lob private byte[] header;
    

    public JPAMailboxMessage() {
        
    }

    @VisibleForTesting
    protected JPAMailboxMessage(byte[] header, byte[] body) {
        this.header = header;
        this.body = body;
    }

    public JPAMailboxMessage(JPAMailbox mailbox, Date internalDate, int size, Flags flags, Content content, int bodyStartOctet, PropertyBuilder propertyBuilder) throws MailboxException {
        super(mailbox, internalDate, flags, size, bodyStartOctet, propertyBuilder);
        try {
            int headerEnd = bodyStartOctet;
            if (headerEnd < 0) {
                headerEnd = 0;
            }
            InputStream stream = content.getInputStream();
            this.header = IOUtils.toByteArray(new BoundedInputStream(stream, headerEnd));
            this.body = IOUtils.toByteArray(stream);

        } catch (IOException e) {
            throw new MailboxException("Unable to parse message",e);
        }
    }

    /**
     * Create a copy of the given message
     */
    public JPAMailboxMessage(JPAMailbox mailbox, MessageUid uid, ModSeq modSeq, MailboxMessage message) throws MailboxException {
        super(mailbox, uid, modSeq, message);
        try {
            this.body = IOUtils.toByteArray(message.getBodyContent());
            this.header = IOUtils.toByteArray(message.getHeaderContent());
        } catch (IOException e) {
            throw new MailboxException("Unable to parse message",e);
        }
    }

    @Override
    public InputStream getBodyContent() throws IOException {
        if (body == null) {
            return new ByteArrayInputStream(EMPTY_ARRAY);
        }
        return new ByteArrayInputStream(body);
    }

    @Override
    public InputStream getHeaderContent() throws IOException {
        if (header == null) {
            return new ByteArrayInputStream(EMPTY_ARRAY);
        }
        return new ByteArrayInputStream(header);
    }

    @Override
    public MailboxMessage copy(Mailbox mailbox) throws MailboxException {
        return new JPAMailboxMessage(JPAMailbox.from(mailbox), getUid(), getModSeq(), this);
    }
}
