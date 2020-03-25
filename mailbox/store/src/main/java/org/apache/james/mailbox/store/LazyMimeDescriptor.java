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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Header;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MimeDescriptor;
import org.apache.james.mailbox.store.mail.model.Message;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;

/**
 * A {@link MimeDescriptor} implementation which tries to optimize the way the data
 * is loading by using it in a lazy fashion whenever possible.
 */
public class LazyMimeDescriptor implements MimeDescriptor {

    private final Message message;
    private final MessageResult result;
    private PropertyBuilder pbuilder;
    
    public LazyMimeDescriptor(MessageResult result, Message message) {
        this.message = message;
        this.result = result;
    }
    
    
    @Override
    public Iterator<Header> headers() throws MailboxException {
        return result.getHeaders().headers();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        try {
            return result.getHeaders().getInputStream();
        } catch (MailboxException e) {
            throw new IOException("Unable to retrieve content", e);
        }
    }

    @Override
    public long size() throws MailboxException {
        return result.getHeaders().size();
    }

    @Override
    public String getMimeType() {
        return message.getMediaType();
    }

    @Override
    public String getMimeSubType() {
        return message.getSubType();
    }

    @Override
    public String getContentID() {
        return getPropertyBuilder().getContentID();
    }

    @Override
    public String getContentDescription() {
        return getPropertyBuilder().getContentDescription();
    }

    @Override
    public String getContentLocation() {
        return getPropertyBuilder().getContentLocation();
    }

    @Override
    public String getContentMD5() {
        return getPropertyBuilder().getContentMD5();
    }

    @Override
    public String getTransferContentEncoding() {
        return getPropertyBuilder().getContentTransferEncoding();
    }

    @Override
    public List<String> getLanguages() {
        return getPropertyBuilder().getContentLanguage();
    }

    @Override
    public String getDisposition() {
        return getPropertyBuilder().getContentDispositionType();
    }

    @Override
    public Map<String, String> getDispositionParams() {
        return getPropertyBuilder().getContentDispositionParameters();

    }

    @Override
    public long getLines() {
        Long count =  message.getTextualLineCount();
        if (count == null) {
            return -1;
        } else {
            return count;
        }
    }

    @Override
    public long getBodyOctets() {
        return message.getBodyOctets();
    }

    @Override
    public Iterator<MimeDescriptor> parts() {
        return Collections.emptyIterator();
    }

    @Override
    public MimeDescriptor embeddedMessage() {
        return null;
    }

    @Override
    public Map<String, String> contentTypeParameters() {
        return getPropertyBuilder().getContentTypeParameters();
    }

    /**
     * Return a {@link PropertyBuilder} which is created in a lazy fashion if it not exist yet.
     * This is done as it may be expensive to retrieve the properties of the message.
     * 
     * @return pbuilder
     */
    private PropertyBuilder getPropertyBuilder() {
        if (pbuilder == null) {
            pbuilder = new PropertyBuilder(message.getProperties());
        }
        return pbuilder;
    }
    
}
