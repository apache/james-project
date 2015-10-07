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
import javax.mail.internet.SharedInputStream;
import javax.mail.util.SharedByteArrayInputStream;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Table;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.jpa.mail.model.JPAMailbox;
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.openjpa.persistence.Persistent;

/**
 * JPA implementation of {@link AbstractJPAMessage} which use openjpas {@link Persistent} type to
 * be able to stream the message content without loading it into the memory at all. 
 * 
 * This is not supported for all DB's yet. See <a href="http://openjpa.apache.org/builds/latest/docs/manual/ref_guide_mapping_jpa.html">Additional JPA Mappings</a>
 * 
 * If your DB is not supported by this, use {@link JPAMessage} 
 *
 * TODO: Fix me!
 */
@Entity(name="Message")
@Table(name="JAMES_MAIL")
public class JPAStreamingMessage extends AbstractJPAMessage {

    @Persistent(optional = false, fetch = FetchType.LAZY)
    @Column(name = "MAIL_BYTES", length = 1048576000, nullable = false)
    private InputStream body;

    @Persistent(optional = false, fetch = FetchType.LAZY)
    @Column(name = "HEADER_BYTES", length = 10485760, nullable = false)
    private InputStream header;

    private SharedInputStream content;

    @Deprecated
    public JPAStreamingMessage() {}

    public JPAStreamingMessage(JPAMailbox mailbox, Date internalDate, int size, Flags flags, SharedInputStream content, int bodyStartOctet,final PropertyBuilder propertyBuilder) throws MailboxException {
        super(mailbox, internalDate, flags, size ,bodyStartOctet, propertyBuilder);
        this.content = content;

        try {
            this.header = getHeaderContent();
            this.body = getBodyContent();

        } catch (IOException e) {
            throw new MailboxException("Unable to parse message",e);
        }
    }

    /**
     * Create a copy of the given message
     * 
     * @param message
     * @throws IOException 
     */
    public JPAStreamingMessage(JPAMailbox mailbox, long uid, long modSeq, Message<?> message) throws MailboxException {
        super(mailbox, uid, modSeq, message);
        try {
            this.content = new SharedByteArrayInputStream(IOUtils.toByteArray(message.getFullContent()));
            this.header = getHeaderContent();
            this.body = getBodyContent();
        } catch (IOException e) {
            throw new MailboxException("Unable to parse message",e);
        }
    }

    
    /**
     * @see org.apache.james.mailbox.store.mail.model.Message#getBodyContent()
     */
    public InputStream getBodyContent() throws IOException {
        return content.newStream(getBodyStartOctet(), -1);
    }

    /**
     * @see org.apache.james.mailbox.store.mail.model.Message#getHeaderContent()
     */
    public InputStream getHeaderContent() throws IOException {
        int headerEnd = getBodyStartOctet() -2;
        if (headerEnd < 0) {
            headerEnd = 0;
        }
        return content.newStream(0, headerEnd);
    }

}
