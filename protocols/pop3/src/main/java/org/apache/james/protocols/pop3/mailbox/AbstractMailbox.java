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

package org.apache.james.protocols.pop3.mailbox;

import java.io.IOException;
import java.io.InputStream;

import org.apache.james.protocols.api.CombinedInputStream;

/**
 * A {@link Mailbox} implementation which use a {@link CombinedInputStream} over {@link #getMessageHeaders(long)}  and {@link #getMessageBody(long)} to return the full message.
 * 
 *
 */
public abstract class AbstractMailbox implements Mailbox {

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.james.protocols.pop3.mailbox.Mailbox#getMessage(String)
     */
    public InputStream getMessage(String uid) throws IOException {
        return new CombinedInputStream(getMessageHeaders(uid), getMessageBody(uid));
    }

    /**
     * Does nothing
     */
    public void close() throws IOException {
        // do nothing
    }

}
