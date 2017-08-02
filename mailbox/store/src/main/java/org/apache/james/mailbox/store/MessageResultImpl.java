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
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.Headers;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MimeDescriptor;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mailbox.store.streaming.InputStreamContent;
import org.apache.james.mailbox.store.streaming.InputStreamContent.Type;
import org.apache.james.mime4j.MimeException;

import com.google.common.base.Objects;

/**
 * Bean based implementation.
 */
public class MessageResultImpl implements MessageResult {

    private final Map<MimePath, PartContent> partsByPath = new HashMap<>();

    private MimeDescriptor mimeDescriptor;

	private final MailboxMessage message;

    private HeadersImpl headers;
    private Content fullContent;
    private Content bodyContent;

    
    public MessageResultImpl(MailboxMessage message) throws IOException {
        this.message = message;
        this.headers = new HeadersImpl(message);
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
    public Date getInternalDate() {
        return message.getInternalDate();
    }

    /**
     * @see org.apache.james.mailbox.model.MessageResult#getFlags()
     */
    public Flags getFlags() {
        return message.createFlags();
    }

    /**
     * @see org.apache.james.mailbox.model.MessageResult#getSize()
     */
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

    /**
     * @see org.apache.james.mailbox.model.MessageResult#getFullContent()
     */
    public final Content getFullContent() throws IOException {
        if (fullContent == null) {
            fullContent = new InputStreamContent(message, Type.Full);
        }
        return fullContent;
    }

    /**
     * @see org.apache.james.mailbox.model.MessageResult#getBody()
     */
    public final Content getBody() throws IOException {
        if (bodyContent == null) {
            bodyContent = new InputStreamContent(message, Type.Body);
        }
        return bodyContent;
    }


    /**
     * Renders suitably for logging.
     * 
     * @return a <code>String</code> representation of this object.
     */
    public String toString() {
        final String TAB = " ";

        return "MessageResultImpl ( " + "uid = " + getUid() + TAB + "flags = " + getFlags() + TAB + "size = " + getSize() + TAB + "internalDate = " + getInternalDate()+ ")";
    }

    /**
     * @see
     * org.apache.james.mailbox.model.MessageResult#getBody(org.apache.james.mailbox.model.MessageResult.MimePath)
     */
    public Content getBody(MimePath path) throws MailboxException {
        final Content result;
        final PartContent partContent = getPartContent(path);
        if (partContent == null) {
            result = null;
        } else {
            result = partContent.getBody();
        }
        return result;
    }

    /**
     * @see
     * org.apache.james.mailbox.model.MessageResult#getMimeBody(org.apache.james.mailbox.model.MessageResult.MimePath)
     */
    public Content getMimeBody(MimePath path) throws MailboxException {
        final Content result;
        final PartContent partContent = getPartContent(path);
        if (partContent == null) {
            result = null;
        } else {
            result = partContent.getMimeBody();
        }
        return result;
    }

    /**
     * @see
     * org.apache.james.mailbox.model.MessageResult#getFullContent(org.apache.james.mailbox.model.MessageResult.MimePath)
     */
    public Content getFullContent(MimePath path) throws MailboxException {
        final Content result;
        final PartContent partContent = getPartContent(path);
        if (partContent == null) {
            result = null;
        } else {
            result = partContent.getFull();
        }
        return result;
    }

    /**
     * @see
     * org.apache.james.mailbox.model.MessageResult#iterateHeaders(org.apache.james.mailbox.model.MessageResult.MimePath)
     */
    public Iterator<Header> iterateHeaders(MimePath path) throws MailboxException {
        final Iterator<Header> result;
        final PartContent partContent = getPartContent(path);
        if (partContent == null) {
            result = null;
        } else {
            result = partContent.getHeaders();
        }
        return result;
    }

    /**
     * @see
     * org.apache.james.mailbox.model.MessageResult#iterateMimeHeaders(org.apache.james.mailbox.model.MessageResult.MimePath)
     */
    public Iterator<Header> iterateMimeHeaders(MimePath path) throws MailboxException {
        final Iterator<Header> result;
        final PartContent partContent = getPartContent(path);
        if (partContent == null) {
            result = null;
        } else {
            result = partContent.getMimeHeaders();
        }
        return result;
    }

    public void setBodyContent(MimePath path, Content content) {
        final PartContent partContent = getPartContent(path);
        partContent.setBody(content);
    }

    public void setMimeBodyContent(MimePath path, Content content) {
        final PartContent partContent = getPartContent(path);
        partContent.setMimeBody(content);
    }

    public void setFullContent(MimePath path, Content content) {
        final PartContent partContent = getPartContent(path);
        partContent.setFull(content);
    }

    public void setHeaders(MimePath path, Iterator<Header> headers) {
        final PartContent partContent = getPartContent(path);
        partContent.setHeaders(headers);
    }

    public void setMimeHeaders(MimePath path, Iterator<Header> headers) {
        final PartContent partContent = getPartContent(path);
        partContent.setMimeHeaders(headers);
    }

    private PartContent getPartContent(MimePath path) {
        PartContent result = (PartContent) partsByPath.get(path);
        if (result == null) {
            result = new PartContent();
            partsByPath.put(path, result);
        }
        return result;
    }

    private static final class PartContent {
        private Content body;

        private Content mimeBody;

        private Content full;

        private Iterator<Header> headers;

        private Iterator<Header> mimeHeaders;

        private int content;

        public Content getBody() {
            return body;
        }

        public void setBody(Content body) {
            content = content | FetchGroup.BODY_CONTENT;
            this.body = body;
        }

        public Content getMimeBody() {
            return mimeBody;
        }

        public void setMimeBody(Content mimeBody) {
            content = content | FetchGroup.MIME_CONTENT;
            this.mimeBody = mimeBody;
        }

        public Content getFull() {
            return full;
        }

        public void setFull(Content full) {
            content = content | FetchGroup.FULL_CONTENT;
            this.full = full;
        }

        public Iterator<Header> getHeaders() {
            return headers;
        }

        public void setHeaders(Iterator<Header> headers) {
            content = content | FetchGroup.HEADERS;
            this.headers = headers;
        }

        public Iterator<Header> getMimeHeaders() {
            return mimeHeaders;
        }

        public void setMimeHeaders(Iterator<Header> mimeHeaders) {
            content = content | FetchGroup.MIME_HEADERS;
            this.mimeHeaders = mimeHeaders;
        }
    }

    /**
     * @see org.apache.james.mailbox.model.MessageResult#getMimeDescriptor()
     */
    public MimeDescriptor getMimeDescriptor() throws MailboxException {
        
        // check if we need to create the MimeDescriptor which is done in a lazy fashion because
        // it can be relative expensive on big messages and slow mailbox implementations
        if (mimeDescriptor == null) {
            try {
                if (MimeDescriptorImpl.isComposite(message.getMediaType())) {
                    mimeDescriptor = MimeDescriptorImpl.build(getFullContent().getInputStream());
                } else {
                    mimeDescriptor = new LazyMimeDescriptor(this, message);
                }
            } catch (IOException e) {
                throw new MailboxException("Unable to create the MimeDescriptor", e);
            } catch (MimeException e) {
                throw new MailboxException("Unable to create the MimeDescriptor", e);
            }
        }
        return mimeDescriptor;
    }

    /**
     * @see org.apache.james.mailbox.model.MessageMetaData#getModSeq()
     */
    public long getModSeq() {
        return message.getModSeq();
    }
    
    @Override
    public Headers getHeaders() throws MailboxException {
        if (headers == null) {
            headers = new HeadersImpl(message);
        }
        return headers;
    }
    
    @Override
    public List<MessageAttachment> getAttachments() throws MailboxException {
        return message.getAttachments();
    }
    
    private final class HeadersImpl implements Headers {

        private final Message msg;
        private List<Header> headers;
        
        public HeadersImpl(Message msg) {
            this.msg = msg;
        }

        @Override
        public int hashCode() {
            return 39 * 19 + message.hashCode();
        }

        @Override
        public boolean equals (Object obj) {
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
        public long size() {
            return msg.getHeaderOctets();
        }

        @Override
        public Iterator<Header> headers() throws MailboxException {
            if (headers == null) {
                try {
                    headers = ResultUtils.createHeaders(message);
                } catch (IOException e) {
                    throw new MailboxException("Unable to parse headers", e);
                }
            }
            return headers.iterator();
        }
        
    }
}
