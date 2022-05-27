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
import static org.apache.james.imap.api.ImapConstants.LINE_END_BYTES;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.message.response.FetchResponse.BodyElement;
import org.apache.james.mailbox.model.Header;

/**
 * {@link BodyElement} which represent a MIME element specified by for example (BODY[1.MIME])
 *
 */
public class MimeBodyElement implements BodyElement {
    private static final String NAME_DELIMITER = ": ";
    private static final byte[] NAME_DELIMITER_BYTES = NAME_DELIMITER.getBytes(US_ASCII);

    private static class ExactSizeByteArrayOutputStream extends ByteArrayOutputStream {
        public ExactSizeByteArrayOutputStream(int size) {
            super(size);
        }

        public byte[] getUnderlyingBuffer() {
            return buf;
        }
    }

    private final String name;
    protected final List<Header> headers;
    protected long size;

    public MimeBodyElement(String name, List<Header> headers) {
        this.name = name;
        this.headers = headers;
        this.size = calculateSize(headers);
    }

    @Override
    public String getName() {
        return name;
    }

    protected long calculateSize(List<Header> headers) {
        if (headers.isEmpty()) {
            return 0;
        } else {
            long count = 0;
            for (Header header : headers) {
                count += header.size() + ImapConstants.LINE_END.length();
            }
            return count + ImapConstants.LINE_END.length();
        }
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        ExactSizeByteArrayOutputStream out = new ExactSizeByteArrayOutputStream((int) size);

        for (Header header : headers) {
            out.write(header.getName().getBytes(US_ASCII));
            out.write(NAME_DELIMITER_BYTES);
            out.write(header.getValue().getBytes(US_ASCII));
            out.write(LINE_END_BYTES);
        }
        // no empty line with CRLF for MIME headers. See IMAP-297
        if (size > 0) {
            out.write(ImapConstants.LINE_END.getBytes());
        }
        return new ByteArrayInputStream(out.getUnderlyingBuffer());
    }
}
