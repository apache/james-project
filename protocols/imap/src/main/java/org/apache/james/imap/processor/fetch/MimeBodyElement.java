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
package org.apache.james.imap.processor.fetch;

import static java.nio.charset.StandardCharsets.US_ASCII;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.message.response.FetchResponse.BodyElement;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Header;

/**
 * {@link BodyElement} which represent a MIME element specified by for example (BODY[1.MIME])
 *
 */
public class MimeBodyElement implements BodyElement {
    private final String name;
    protected final List<Header> headers;
    protected long size;

    public MimeBodyElement(String name, List<Header> headers) throws MailboxException {
        this.name = name;
        this.headers = headers;
        this.size = calculateSize(headers);
    }

    @Override
    public String getName() {
        return name;
    }
    

    protected long calculateSize(List<Header> headers) throws MailboxException {
        final int result;
        if (headers.isEmpty()) {
           result = 0;
        } else {
            int count = 0;
            for (Header header : headers) {
                count += header.size() + ImapConstants.LINE_END.length();
            }
            result = count + ImapConstants.LINE_END.length();
        }
        return result;
    }

    @Override
    public long size() {
        return size;
    }


    @Override
    public InputStream getInputStream() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        for (Header header : headers) {
            out.write((header.getName() + ": " + header.getValue() + ImapConstants.LINE_END).getBytes(US_ASCII));
        }
        // no empty line with CRLF for MIME headers. See IMAP-297
        if (size > 0) {
            out.write(ImapConstants.LINE_END.getBytes());
        }
        return new ByteArrayInputStream(out.toByteArray());
    }


}
