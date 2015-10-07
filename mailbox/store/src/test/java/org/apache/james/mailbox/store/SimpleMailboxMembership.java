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
package org.apache.james.mailbox.store;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.SequenceInputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.mail.Flags;

import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mailbox.store.mail.model.Property;

public class SimpleMailboxMembership implements Message<TestId> {
    
    private static final String TOSTRING_SEPARATOR = " ";
    
    public TestId mailboxId;
    public long uid;
    public Date internalDate;
    public boolean recent = false;
    public boolean answered = false;
    public boolean deleted = false;
    public boolean draft = false;
    public boolean flagged = false;
    public boolean seen = false;

    public SimpleMailboxMembership(TestId mailboxId, long uid, long modSeq, Date internalDate, int size, 
            Flags flags, byte[] body, final Map<String, String> headers) throws Exception {
        super();
        this.mailboxId = mailboxId;
        this.uid = uid;
        this.internalDate = internalDate;
        this.size = size;
        this.body = body;
        final Map<String,String> originalHeaders = headers;
        if (originalHeaders == null) {
            this.headers = new HashMap<String,String>();
        } else {
            this.headers = originalHeaders;
        }
        
        this.body =  body;
        setFlags(flags);
    }

    /**
     * @see org.apache.james.imap.Message.mail.model.Document#getInternalDate()
     */
    public Date getInternalDate() {
        return internalDate;
    }

    /**
     * @see org.apache.james.imap.Message.mail.model.Document#getMailboxId()
     */
    public TestId getMailboxId() {
        return mailboxId;
    }
    
    /**
     * @see org.apache.james.imap.Message.mail.model.Document#getUid()
     */
    public long getUid() {
        return uid;
    }

    /**
     * @see org.apache.james.imap.Message.mail.model.Document#isAnswered()
     */
    public boolean isAnswered() {
        return answered;
    }

    /**
     * @see org.apache.james.imap.Message.mail.model.Document#isDeleted()
     */
    public boolean isDeleted() {
        return deleted;
    }

    /**
     * @see org.apache.james.imap.Message.mail.model.Document#isDraft()
     */
    public boolean isDraft() {
        return draft;
    }

    /**
     * @see org.apache.james.imap.Message.mail.model.Document#isFlagged()
     */
    public boolean isFlagged() {
        return flagged;
    }

    /**
     * @see org.apache.james.imap.Message.mail.model.Document#isRecent()
     */
    public boolean isRecent() {
        return recent;
    }

    /**
     * @see org.apache.james.imap.Message.mail.model.Document#isSeen()
     */
    public boolean isSeen() {
        return seen;
    }

    /**
     * @see org.apache.james.imap.Message.mail.model.Document#unsetRecent()
     */
    public void unsetRecent() {
        recent = false;
    }
    
    /**
     * @see org.apache.james.imap.Message.mail.model.Document#setFlags(javax.mail.Flags)
     */
    public void setFlags(Flags flags) {
        answered = flags.contains(Flags.Flag.ANSWERED);
        deleted = flags.contains(Flags.Flag.DELETED);
        draft = flags.contains(Flags.Flag.DRAFT);
        flagged = flags.contains(Flags.Flag.FLAGGED);
        recent = flags.contains(Flags.Flag.RECENT);
        seen = flags.contains(Flags.Flag.SEEN);
    }

    /**
     * @see org.apache.james.imap.Message.mail.model.Document#createFlags()
     */
    public Flags createFlags() {
        final Flags flags = new Flags();

        if (isAnswered()) {
            flags.add(Flags.Flag.ANSWERED);
        }
        if (isDeleted()) {
            flags.add(Flags.Flag.DELETED);
        }
        if (isDraft()) {
            flags.add(Flags.Flag.DRAFT);
        }
        if (isFlagged()) {
            flags.add(Flags.Flag.FLAGGED);
        }
        if (isRecent()) {
            flags.add(Flags.Flag.RECENT);
        }
        if (isSeen()) {
            flags.add(Flags.Flag.SEEN);
        }
        return flags;
    }

    @Override
    public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = PRIME * result + (int) (mailboxId.id ^ (mailboxId.id >>> 32));
        result = PRIME * result + (int) (uid ^ (uid >>> 32));
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final Message<TestId> other = (Message<TestId>) obj;
        if (mailboxId.id != other.getMailboxId().id)
            return false;
        if (uid != other.getUid())
            return false;
        return true;
    }

    public String toString()
    {
        final String retValue = 
            "mailbox("
            + "mailboxId = " + this.mailboxId + TOSTRING_SEPARATOR
            + "uid = " + this.uid + TOSTRING_SEPARATOR
            + "internalDate = " + this.internalDate + TOSTRING_SEPARATOR
            + "size = " + this.size + TOSTRING_SEPARATOR
            + "answered = " + this.answered + TOSTRING_SEPARATOR
            + "deleted = " + this.deleted + TOSTRING_SEPARATOR
            + "draft = " + this.draft + TOSTRING_SEPARATOR
            + "flagged = " + this.flagged + TOSTRING_SEPARATOR
            + "recent = " + this.recent + TOSTRING_SEPARATOR
            + "seen = " + this.seen + TOSTRING_SEPARATOR
            + " )";

        return retValue;
    }

    
    public static final char[] NEW_LINE = { 0x0D, 0x0A };
    
    public byte[] body;
    public Map<String, String> headers;
    public List<SimpleProperty> properties;
    public String subType = null;
    public String mediaType = null;
    public Long textualLineCount = null;

    private int size;

    private long modSeq;
    

    /**
     * @throws IOException 
     * @see org.apache.james.imap.Message.mail.model.Document#getBodyContent()
     */
    public InputStream getBodyContent() throws IOException {
        return new ByteArrayInputStream(body);
    }

    /**
     * @see org.apache.james.mailbox.store.mail.model.Message#getHeaderContent()
     */
    public InputStream getHeaderContent() throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final Writer writer = new OutputStreamWriter(baos, "us-ascii");

        Iterator<Entry<String, String>> hIt = headers.entrySet().iterator();
        while (hIt.hasNext()) {
            Entry<String, String> header = hIt.next();
            writer.write(header.getKey());
            writer.write(": ");
            writer.write(header.getValue());
            writer.write(NEW_LINE);
        }
        writer.write(NEW_LINE);
        writer.flush();
        return new ByteArrayInputStream(baos.toByteArray());

    }

    public long getBodyOctets() {
        return body.length;
    }

    public String getSubType() {
        return subType;
    }

    public String getMediaType() {
        return mediaType;
    }

    public List<Property> getProperties() {
        return new ArrayList<Property>(properties);
    }

    public Long getTextualLineCount() {
        return textualLineCount;
    }

    public long getFullContentOctets() {
        return size;
    }

    /**
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(Message<TestId> other) {
        return (int) (getUid() - other.getUid());
    }

    public long getModSeq() {
        return modSeq;
    }

    public void setModSeq(long modSeq) {
        this.modSeq = modSeq;
    }

    public void setUid(long uid) {
        this.uid = uid;
    }

    @Override
    public InputStream getFullContent() throws IOException {
        return new SequenceInputStream(getHeaderContent(), getBodyContent());
    }
    
    

}
