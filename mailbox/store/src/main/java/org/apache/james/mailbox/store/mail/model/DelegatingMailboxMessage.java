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
package org.apache.james.mailbox.store.mail.model;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;

import javax.mail.Flags;

import org.apache.james.mailbox.store.mail.model.impl.MessageUidComparator;

public abstract class DelegatingMailboxMessage implements MailboxMessage {

    private static final MessageUidComparator MESSAGE_UID_COMPARATOR = new MessageUidComparator();
    
    private final Message message;

    protected DelegatingMailboxMessage(Message message) {
        this.message = message;
    }

    @Override
    public int compareTo(MailboxMessage other) {
        return MESSAGE_UID_COMPARATOR.compare(this, other);
    }

    @Override
    public final Flags createFlags() {
        return FlagsBuilder.createFlags(this, createUserFlags());
    }

    /**
     * Return all stored user flags or null if none are stored. By default this return null as no user flags are stored
     * permanent. This method SHOULD get overridden, If the implementation supports to store user flags.
     * 
     * @return userFlags
     */
    protected String[] createUserFlags() {
        return null;
    }

    @Override
    public long getBodyOctets() {
        return message.getBodyOctets();
    }

    @Override
    public long getFullContentOctets() {
        return message.getFullContentOctets();
    }

    @Override
    public Long getTextualLineCount() {
        return message.getTextualLineCount();
    }

    @Override
    public InputStream getHeaderContent() throws IOException {
        return message.getHeaderContent();
    }

    @Override
    public InputStream getFullContent() throws IOException {
        return message.getFullContent();
    }

    @Override
    public List<Property> getProperties() {
        return message.getProperties();
    }

    @Override
    public Date getInternalDate() {
        return message.getInternalDate();
    }

    @Override
    public InputStream getBodyContent() throws IOException {
        return message.getBodyContent();
    }

    @Override
    public String getMediaType() {
        return message.getMediaType();
    }

    @Override
    public String getSubType() {
        return message.getSubType();
    }

    @Override
    public DefaultMessageId getMessageId() {
        return new DefaultMessageId(getMailboxId(), getUid());
    }

    public Message getMessage() {
        return message;
    }

    @Override
    public List<AttachmentId> getAttachmentsIds() {
        return message.getAttachmentsIds();
    }
}
