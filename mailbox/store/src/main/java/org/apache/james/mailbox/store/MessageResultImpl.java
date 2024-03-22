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

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.Header;
import org.apache.james.mailbox.model.Headers;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MimeDescriptor;
import org.apache.james.mailbox.model.MimePath;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.streaming.InputStreamContent;
import org.apache.james.mailbox.store.streaming.InputStreamContent.Type;
import org.apache.james.mime4j.MimeException;
import org.reactivestreams.Publisher;

import com.google.common.base.Objects;

/**
 * Bean based implementation.
 */
public class MessageResultImpl implements MessageResult {
    private static final String TAB = " ";

    private final Map<MimePath, PartContent> partsByPath = new HashMap<>();
    private final MailboxMessage message;
    private final HeadersImpl headers;

    private MimeDescriptor mimeDescriptor;
    private Content fullContent;
    private Content bodyContent;

    public MessageResultImpl(MailboxMessage message) {
        this.message = message;
        this.headers = new HeadersImpl(message);
    }

    @Override
    public MessageMetaData messageMetaData() {
        return message.metaData();
    }

    @Override
    public MailboxId getMailboxId() {
        return message.getMailboxId();
    }

    @Override
    public MessageUid getUid() {
        return message.getUid();
    }

    @Override
    public MessageId getMessageId() {
        return message.getMessageId();
    }

    @Override
    public ThreadId getThreadId() {
        return message.getThreadId();
    }

    @Override
    public Optional<Date> getSaveDate() {
        return message.getSaveDate();
    }

    @Override
    public Date getInternalDate() {
        return message.getInternalDate();
    }

    @Override
    public Flags getFlags() {
        return message.createFlags();
    }

    @Override
    public ModSeq getModSeq() {
        return message.getModSeq();
    }

    @Override
    public long getSize() {
        return message.getFullContentOctets();
    }

    @Override
    public int compareTo(MessageResult that) {
        return this.getUid().compareTo(that.getUid());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getUid());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof MessageResultImpl) {
            MessageResultImpl that = (MessageResultImpl)obj;
            return this.headers.equals(that.headers) && this.message.equals(that.message);
        }
        return false;
    }

    @Override
    public final Content getFullContent() throws IOException {
        if (fullContent == null) {
            fullContent = new InputStreamContent(message, Type.FULL);
        }
        return fullContent;
    }

    @Override
    public final Content getBody() throws IOException {
        if (bodyContent == null) {
            bodyContent = new InputStreamContent(message, Type.BODY);
        }
        return bodyContent;
    }


    /**
     * Renders suitably for logging.
     * 
     * @return a <code>String</code> representation of this object.
     */
    public String toString() {
        return "MessageResultImpl ( " + "uid = " + getUid() + TAB + "flags = " + getFlags() + TAB + "size = " + getSize()
            + TAB + "internalDate = " + getInternalDate() + ")";
    }

    @Override
    public Content getBody(MimePath path) {
        PartContent partContent = getPartContent(path);
        return partContent.getBody();
    }

    @Override
    public Content getMimeBody(MimePath path) {
        PartContent partContent = getPartContent(path);
        return partContent.getMimeBody();
    }

    @Override
    public Content getFullContent(MimePath path) {
        PartContent partContent = getPartContent(path);
        return partContent.getFull();
    }

    @Override
    public Iterator<Header> iterateHeaders(MimePath path) {
        PartContent partContent = getPartContent(path);
        return partContent.getHeaders();
    }

    @Override
    public Iterator<Header> iterateMimeHeaders(MimePath path) {
        PartContent partContent = getPartContent(path);
        return partContent.getMimeHeaders();
    }

    void setBodyContent(MimePath path, Content content) {
        PartContent partContent = getPartContent(path);
        partContent.setBody(content);
    }

    void setMimeBodyContent(MimePath path, Content content) {
        PartContent partContent = getPartContent(path);
        partContent.setMimeBody(content);
    }

    void setFullContent(MimePath path, Content content) {
        PartContent partContent = getPartContent(path);
        partContent.setFull(content);
    }

    void setHeaders(MimePath path, Iterator<Header> headers) {
        PartContent partContent = getPartContent(path);
        partContent.setHeaders(headers);
    }

    void setMimeHeaders(MimePath path, Iterator<Header> headers) {
        PartContent partContent = getPartContent(path);
        partContent.setMimeHeaders(headers);
    }

    private PartContent getPartContent(MimePath path) {
        return partsByPath.computeIfAbsent(path, any -> new PartContent());
    }

    private static final class PartContent {
        private Content body;

        private Content mimeBody;

        private Content full;

        private Iterator<Header> headers;

        private Iterator<Header> mimeHeaders;


        public Content getBody() {
            return body;
        }

        public void setBody(Content body) {
            this.body = body;
        }

        Content getMimeBody() {
            return mimeBody;
        }

        void setMimeBody(Content mimeBody) {
            this.mimeBody = mimeBody;
        }

        public Content getFull() {
            return full;
        }

        public void setFull(Content full) {
            this.full = full;
        }

        public Iterator<Header> getHeaders() {
            return headers;
        }

        public void setHeaders(Iterator<Header> headers) {
            this.headers = headers;
        }

        Iterator<Header> getMimeHeaders() {
            return mimeHeaders;
        }

        void setMimeHeaders(Iterator<Header> mimeHeaders) {
            this.mimeHeaders = mimeHeaders;
        }
    }

    @Override
    public MimeDescriptor getMimeDescriptor() throws MailboxException {
        
        // check if we need to create the MimeDescriptor which is done in a lazy fashion because
        // it can be relative expensive on big messages and slow mailbox implementations
        if (mimeDescriptor == null) {
            try {
                if (isComposite(message.getMediaType())) {
                    mimeDescriptor = MimeDescriptorImpl.build(getFullContent().getInputStream());
                } else {
                    mimeDescriptor = new LazyMimeDescriptor(this, message);
                }
            } catch (IOException | MimeException e) {
                throw new MailboxException("Unable to create the MimeDescriptor", e);
            }
        }
        return mimeDescriptor;
    }

    /**
     * Is this a composite media type (as per RFC2045)?
     *
     * TODO: Move to Mime4j
     * @param mediaType possibly null
     * @return true when the type is composite,
     * false otherwise
     */
    private boolean isComposite(String mediaType) {
        return "message".equalsIgnoreCase(mediaType) || "multipart".equalsIgnoreCase(mediaType);
    }
    
    @Override
    public Headers getHeaders() {
        return headers;
    }
    
    @Override
    public List<MessageAttachmentMetadata> getLoadedAttachments() {
        return message.getAttachments();
    }

    private static final class HeadersImpl implements Headers {

        private final MailboxMessage msg;
        private List<Header> headers;
        
        private HeadersImpl(MailboxMessage msg) {
            this.msg = msg;
        }

        @Override
        public int hashCode() {
            return 39 * 19 + msg.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof HeadersImpl) {
                return msg.equals(((HeadersImpl)obj).msg);
            }
            return false;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return msg.getHeaderContent();
        }

        @Override
        public Publisher<ByteBuffer> reactiveBytes() {
            return msg.getHeaderContentReactive();
        }

        @Override
        public long size() {
            return msg.getHeaderOctets();
        }

        @Override
        public Optional<byte[][]> asBytesSequence() {
            return msg.getHeadersBytes();
        }

        @Override
        public Iterator<Header> headers() throws MailboxException {
            if (headers == null) {
                try {
                    headers = ResultUtils.createHeaders(msg);
                } catch (IOException e) {
                    throw new MailboxException("Unable to parse headers", e);
                }
            }
            return headers.iterator();
        }
    }
}
