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
package org.apache.james.mailbox.store;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MessageResult;

public final class ResultHeader implements MessageResult.Header {
    private final String name;

    private final String value;

    private final long size;
    private final static Charset US_ASCII = Charset.forName("US-ASCII");

    public ResultHeader(String name, String value) {
        this.name = name;
        this.value = value;
        size = name.length() + value.length() + 2;
    }

    /**
     * @see org.apache.james.mailbox.model.MessageResult.Header#getName()
     */
    public String getName() throws MailboxException {
        return name;
    }

    /**
     * @see org.apache.james.mailbox.model.MessageResult.Header#getValue()
     */
    public String getValue() throws MailboxException {
        return value;
    }

    /**
     * @see org.apache.james.mailbox.model.Content#size()
     */
    public long size() {
        return size;
    }

    public String toString() {
        return "[HEADER " + name + ": " + value + "]";
    }

    /**
     * @see org.apache.james.mailbox.model.InputStreamContent#getInputStream()
     */
    public InputStream getInputStream() throws IOException {
        return new ByteArrayInputStream((name + ": " + value).getBytes(US_ASCII));
    }
}