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

package org.apache.james.imap.decode;

import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.process.ImapSession;

/**
 * <p>
 * Parses IMAP request into a command message.
 * </p>
 * <p>
 * <strong>Note:</strong> this is a transitional API and is liable to change.
 * </p>
 */
public interface ImapCommandParser {
    /**
     * Parses IMAP request. TODO: consider error handling
     * 
     * @param request
     *            <code>ImapRequestLineReader</code>, not null
     * @param tag
     *            not null
     * @param session
     *            the {@link ImapSession}
     * @return <code>ImapCommandMessage</code>
     */
    ImapMessage parse(ImapRequestLineReader request, String tag, ImapSession session);
}
