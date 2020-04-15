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
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.james.mailbox.model.Content;
import org.apache.james.mailbox.model.Header;

/**
 * Abstract base class for {@link Content} implementations which hold the headers and 
 * the body a email
 */
public class FullByteContent implements Content {

    private final List<Header> headers;
    private final byte[] body;
    private final long size;
    
    public FullByteContent(byte[] body, List<Header> headers) {
        this.headers = headers;
        this.body = body;
        this.size = computeSize();
    }
    
    protected long computeSize() {
        long result = body.length;
        result += 2;
        for (Header header : headers) {
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
        for (Header header : headers) {
            if (header != null) {
                out.write((header.getName() + ": " + header.getValue() + "\r\n").getBytes(StandardCharsets.US_ASCII));
            }
        }
        out.write("\r\n".getBytes(StandardCharsets.US_ASCII));
        out.flush();
        return new SequenceInputStream(new ByteArrayInputStream(out.toByteArray()), new ByteArrayInputStream(body));
    }

    @Override
    public long size() {
        return size;
    }
    

    
}
