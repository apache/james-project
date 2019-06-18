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
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import javax.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.Headers;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageRange.Type;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResult.FetchGroup;
import org.apache.james.mailbox.model.MessageResultIterator;
import org.apache.james.mailbox.model.MimeDescriptor;
import org.apache.james.mailbox.store.mail.MessageMapper;
import org.apache.james.mailbox.store.mail.MessageMapper.FetchType;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StoreMessageResultIterator implements MessageResultIterator {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoreMessageResultIterator.class);

    private Iterator<MailboxMessage> next = null;
    private MailboxException exception;
    private final Mailbox mailbox;
    private final FetchGroup group;
    private final MessageUid from;
    private MessageUid cursor;
    private final MessageUid to;
    private final BatchSizes batchSizes;
    private final Type type;
    private final MessageMapper mapper;
    private final FetchType ftype;

    public StoreMessageResultIterator(MessageMapper mapper, Mailbox mailbox, MessageRange range, BatchSizes batchSizes, org.apache.james.mailbox.model.MessageResult.FetchGroup group) {
        this.mailbox = mailbox;
        this.group = group;
        this.mapper = mapper;
        this.from = range.getUidFrom();
        this.cursor = this.from;
        this.to = range.getUidTo();
        this.batchSizes = batchSizes;
        this.type = range.getType();
        this.ftype = getFetchType(group);
        LOGGER.debug("batchSizes used: {}", batchSizes);
    }

    /**
     * Use the passed {@link FetchGroup} and calculate the right
     * {@link FetchType} for it
     * 
     * @param group
     * @return fetchType
     */
    private static FetchType getFetchType(FetchGroup group) {
        int content = group.content();
        boolean headers = false;
        boolean body = false;
        boolean full = false;

        if ((content & FetchGroup.HEADERS) > 0) {
            headers = true;
            content -= FetchGroup.HEADERS;
        }
        if (group.getPartContentDescriptors().size() > 0) {
            full = true;
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
        if (cursor.compareTo(to) > 0) {
            return false;
        }

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
        next = mapper.findInMailbox(mailbox, range, ftype, batchSizeFromFetchType(ftype));
    }

    private int batchSizeFromFetchType(FetchType fetchType) {
        switch (fetchType) {
        case Metadata:
            return batchSizes.getFetchMetadata();
        case Headers:
            return batchSizes.getFetchHeaders();
        case Body:
            return batchSizes.getFetchBody();
        case Full:
            return batchSizes.getFetchFull();
        }
        throw new RuntimeException("Unknown fetchTpe: " + fetchType);
    }

    @Override
    public MessageResult next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        
        final MailboxMessage message = next.next();
        MessageResult result;
        try {
            result = ResultUtils.loadMessageResult(message, group);
            cursor = result.getUid();
        } catch (MailboxException e) {
            result = new UnloadedMessageResult(message, e);
        }

        cursor = cursor.next();
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

    private static final class UnloadedMessageResult implements MessageResult {
        private final MailboxException exception;

        private final MessageMetaData messageMetaData;
        private final MessageId messageId;
        private final MailboxId mailboxId;

        public UnloadedMessageResult(MailboxMessage message, MailboxException exception) {
            messageMetaData = message.metaData();
            mailboxId = message.getMailboxId();
            messageId = message.getMessageId();
            this.exception = exception;
        }

        @Override
        public MessageMetaData messageMetaData() {
            return messageMetaData;
        }

        @Override
        public MessageUid getUid() {
            return messageMetaData().getUid();
        }

        @Override
        public MessageId getMessageId() {
            return messageMetaData().getMessageId();
        }

        @Override
        public Date getInternalDate() {
            return messageMetaData().getInternalDate();
        }

        @Override
        public Flags getFlags() {
            return messageMetaData().getFlags();
        }

        @Override
        public long getModSeq() {
            return messageMetaData().getModSeq();
        }

        @Override
        public long getSize() {
            return messageMetaData().getSize();
        }

        @Override
        public MailboxId getMailboxId() {
            return mailboxId;
        }

        @Override
        public Content getFullContent() throws MailboxException {
            throw exception;
        }

        @Override
        public Content getBody() throws MailboxException {
            throw exception;
        }
        
        @Override
        public int compareTo(MessageResult that) {
            return getUid().compareTo(that.getUid());
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof UnloadedMessageResult) {
                UnloadedMessageResult that = (UnloadedMessageResult) o;

                return Objects.equals(exception, that.exception)
                    && Objects.equals(this.getInternalDate(), that.getInternalDate())
                    && Objects.equals(this.getSize(), that.getSize())
                    && Objects.equals(this.getUid(), that.getUid())
                    && Objects.equals(this.getFlags(), that.getFlags())
                    && Objects.equals(this.getModSeq(), that.getModSeq())
                    && Objects.equals(this.messageId, that.messageId);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(exception, getInternalDate(), getSize(), getUid(), getFlags(), getModSeq(), messageId);
        }

        @Override
        public Content getFullContent(MimePath path) throws MailboxException {
            throw exception;
        }

        @Override
        public Iterator<Header> iterateHeaders(MimePath path) throws MailboxException {
            throw exception;
        }

        @Override
        public Iterator<Header> iterateMimeHeaders(MimePath path) throws MailboxException {
            throw exception;
        }

        @Override
        public Content getBody(MimePath path) throws MailboxException {
            throw exception;
        }

        @Override
        public Content getMimeBody(MimePath path) throws MailboxException {
            throw exception;
        }

        @Override
        public MimeDescriptor getMimeDescriptor() throws MailboxException {
            throw exception;
        }

        @Override
        public Headers getHeaders() throws MailboxException {
            throw exception;
        }

        @Override
        public List<MessageAttachment> getAttachments() throws MailboxException {
            throw exception;
        }

    }

}
