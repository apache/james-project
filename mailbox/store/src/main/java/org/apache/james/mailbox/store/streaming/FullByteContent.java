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
package org.apache.james.mailbox.store.streaming;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Iterator;
import java.util.List;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResult.Header;

/**
 * Abstract base class for {@link Content} implementations which hold the headers and 
 * the body a email
 *
 */
public class FullByteContent implements Content {


    private List<Header> headers;
    private byte[] body;
    private long size;
    
    public FullByteContent(final byte[] body, final List<MessageResult.Header> headers) throws MailboxException {
        this.headers = headers;
        this.body = body;
        this.size = caculateSize();
    }
    
    protected long caculateSize() throws MailboxException{
        long result = body.length;
        result += 2;
        for (final Iterator<MessageResult.Header> it = headers.iterator(); it.hasNext();) {
            final MessageResult.Header header = it.next();
            if (header != null) {
                result += header.size();
                result += 2;
            }
        }
        return result;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (final Iterator<MessageResult.Header> it = headers.iterator(); it.hasNext();) {
            final MessageResult.Header header = it.next();
            if (header != null) {
                try {
                    out.write((header.getName() + ": " + header.getValue() + "\r\n").getBytes("US-ASCII"));
                } catch (MailboxException e) {
                    throw new IOException("Unable to read headers", e);
                }
            }
        }
        out.write("\r\n".getBytes("US-ASCII"));
        out.flush();
        return new SequenceInputStream(new ByteArrayInputStream(out.toByteArray()), new ByteArrayInputStream(body));
    }

    @Override
    public long size() {
        return size;
    }
    

    
}
