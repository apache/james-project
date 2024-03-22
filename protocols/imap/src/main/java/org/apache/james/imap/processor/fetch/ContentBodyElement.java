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

/**
 * 
 */
package org.apache.james.imap.processor.fetch;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.apache.james.imap.message.response.FetchResponse.BodyElement;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Content;

class ContentBodyElement implements BodyElement {
    private final String name;
    protected final Content content;

    public ContentBodyElement(String name, Content content) {
        this.name = name;
        this.content = content;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long size() throws IOException {
        try {
            return content.size();
        } catch (MailboxException e) {
            throw new IOException("Unable to get size for body element", e);
        }
    }
    
    @Override
    public Optional<byte[][]> asBytesSequence() {
        return content.asBytesSequence();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return content.getInputStream();
    }
}