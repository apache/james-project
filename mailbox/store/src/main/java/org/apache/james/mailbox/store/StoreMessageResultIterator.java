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

import java.util.Date;
import java.util.Iterator;
import java.util.NoSuchElementException;

import javax.mail.Flags;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.Headers;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageRange.Type;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResult.FetchGroup;
import org.apache.james.mailbox.model.MessageResultIterator;
import org.apache.james.mailbox.model.MimeDescriptor;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.MailboxId;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.Message;

public class StoreMessageResultIterator<Id extends MailboxId> implements MessageResultIterator {

    private Iterator<Message<Id>> next = null;
    private MailboxException exception;
    private Mailbox<Id> mailbox;
    private FetchGroup group;
    private long from;
    private long cursor;
    private long to;
    private int batchSize;
    private Type type;
    private MessageMapper<Id> mapper;
    private FetchType ftype;

    public StoreMessageResultIterator(MessageMapper<Id> mapper, Mailbox<Id> mailbox, MessageRange range, int batchSize, org.apache.james.mailbox.model.MessageResult.FetchGroup group) {
        this.mailbox = mailbox;
        this.group = group;
        this.mapper = mapper;
        this.from = range.getUidFrom();
        this.cursor = this.from;
        this.to = range.getUidTo();
        this.batchSize = batchSize;
        this.type = range.getType();
        this.ftype = getFetchType(group);
    }

    /**
     * Use the passed {@link FetchGroup} and calculate the right
     * {@link FetchType} for it
     * 
     * @param group
     * @return fetchType
     */
    private static final FetchType getFetchType(FetchGroup group) {
        int content = group.content();
        boolean headers = false;
        boolean body = false;
        boolean full = false;

        if ((content & FetchGroup.HEADERS) > 0) {
            headers = true;
            content -= FetchGroup.HEADERS;
        }
        if ((content & FetchGroup.BODY_CONTENT) > 0) {
            body = true;
            content -= FetchGroup.BODY_CONTENT;
        }

        if ((content & FetchGroup.FULL_CONTENT) > 0) {
            full = true;
            content -= FetchGroup.FULL_CONTENT;
        }

        if ((content & FetchGroup.MIME_DESCRIPTOR) > 0) {
            // If we need the mimedescriptor we MAY need the full content later
            // too.
            // This gives us no other choice then request it
            full = true;
            content -= FetchGroup.MIME_DESCRIPTOR;
        }
        if (full || (body && headers)) {
            return FetchType.Full;
        } else if (body) {
            return FetchType.Body;
        } else if (headers) {
            return FetchType.Headers;
        } else {
            return FetchType.Metadata;
        }
    }

    @Override
    public boolean hasNext() {
        if (cursor > to) 
          return false;

        if (next == null || !next.hasNext()) {
            try {
                readBatch();
            } catch (MailboxException e) {
                this.exception = e;
                return false;
            }
        }
        
        return next.hasNext();
    }

    private void readBatch() throws MailboxException {
        MessageRange range;
        switch (type) {
        default:
        case ALL:
            // In case of all, we start on cursor and don't specify a to
            range = MessageRange.from(cursor);
            break;
        case FROM:
            range = MessageRange.from(cursor);
            break;
        case ONE:
            range = MessageRange.one(cursor);
            break;
        case RANGE:
            range = MessageRange.range(cursor, to);
            break;
        }
        next = mapper.findInMailbox(mailbox, range, ftype, batchSize);
    }

    @Override
    public MessageResult next() {
        if (next == null || !next.hasNext())
          throw new NoSuchElementException();
        
        final Message<Id> message = next.next();
        MessageResult result;
        try {
            result = ResultUtils.loadMessageResult(message, group);
            cursor = result.getUid();
        } catch (MailboxException e) {
            result = new UnloadedMessageResult<Id>(message, e);
        }

        cursor++;
        return result;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Read only");
    }

    @Override
    public MailboxException getException() {
        return exception;
    }

    private static final class UnloadedMessageResult<Id extends MailboxId> implements MessageResult {
        private final MailboxException exception;

        private final Date internalDate;

        private final long size;

        private final long uid;

        private final Flags flags;

        private long modSeq = -1;

        public UnloadedMessageResult(final Message<Id> message, final MailboxException exception) {
            super();
            internalDate = message.getInternalDate();
            size = message.getFullContentOctets();
            uid = message.getUid();
            flags = message.createFlags();
            modSeq = message.getModSeq();
            this.exception = exception;
        }

        public Flags getFlags() {
            return flags;
        }

        public Content getFullContent() throws MailboxException {
            throw exception;
        }

        public Date getInternalDate() {
            return internalDate;
        }

        public Content getBody() throws MailboxException {
            throw exception;
        }

        public long getSize() {
            return size;
        }

        public long getUid() {
            return uid;
        }

        public int compareTo(MessageResult that) {
            // Java 1.5 return (int) Math.signum(uid - that.getUid());
            long diff = uid - that.getUid();
            return (int) diff == 0 ? 0 : diff > 0 ? 1 : -1;
        }

        @Override
        public int hashCode() {
            int ret = 19 * 37;
            ret = ret * 37 + exception.hashCode();
            ret = ret * 37 + internalDate.hashCode();
            ret = ret * 37 + (int)size;
            ret = ret * 37 + (int)uid;
            ret = ret * 37 + flags.hashCode();
            ret = ret * 37 + (int)modSeq;
            return ret;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof UnloadedMessageResult) {
                @SuppressWarnings("unchecked")
                UnloadedMessageResult<Id> that = (UnloadedMessageResult<Id>)obj;
                return (size == that.size) && (uid == that.uid) && (modSeq == that.modSeq) && exception.equals(that.exception)
                        && internalDate.equals(that.internalDate) && flags.equals(that.flags);
            }
            return false;
        }

        public Content getFullContent(MimePath path) throws MailboxException {
            throw exception;
        }

        public Iterator<Header> iterateHeaders(MimePath path) throws MailboxException {
            throw exception;
        }

        public Iterator<Header> iterateMimeHeaders(MimePath path) throws MailboxException {
            throw exception;
        }

        public Content getBody(MimePath path) throws MailboxException {
            throw exception;
        }

        public Content getMimeBody(MimePath path) throws MailboxException {
            throw exception;
        }

        public MimeDescriptor getMimeDescriptor() throws MailboxException {
            throw exception;
        }

        public long getModSeq() {
            return modSeq;
        }

        @Override
        public Headers getHeaders() throws MailboxException {
            throw exception;
        }

    }

}
