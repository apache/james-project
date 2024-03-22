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

package org.apache.james.imap.message;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Content;

public interface Literal {
    /**
     * Size of the literal content data.
     * 
     * @return number of octets which will be
     *         put onto the channel
     */
    long size() throws IOException;


    /**
     * Return the Literal as {@link InputStream}
     * 
     * @return elementIn
     */
    InputStream getInputStream() throws IOException;

    default Optional<byte[][]> asBytesSequence() {
        return Optional.empty();
    }

    default Content asMailboxContent() {
        Literal literal = this;
        return new Content() {
            @Override
            public InputStream getInputStream() throws IOException {
                return literal.getInputStream();
            }

            @Override
            public long size() throws MailboxException {
                try {
                    return literal.size();
                } catch (IOException e) {
                    throw new MailboxException("Error computing content size", e);
                }
            }
        };
    }
}
