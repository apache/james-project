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

package org.apache.james.protocols.pop3;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;

import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.StreamResponse;

/**
 * {@link StreamResponse} implementation which allows to write back big-data to the client for POP3
 *
 */
public class POP3StreamResponse extends POP3Response implements StreamResponse {

    private final InputStream stream;

    public POP3StreamResponse(String code, CharSequence description, InputStream stream) {
        super(code, description);
        this.stream = stream;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.StreamResponse#getStream()
     */
    public InputStream getStream() {
        return new SequenceInputStream(stream, new ByteArrayInputStream(".\r\n".getBytes()));
    }

    /**
     * Throws {@link UnsupportedOperationException}
     */
    @Override
    public Response immutable() {
        throw new UnsupportedOperationException("POP3StreamResponse can only be used once, so its not supported to reuse it");
    }

}
