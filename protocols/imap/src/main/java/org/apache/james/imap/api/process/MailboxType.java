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
package org.apache.james.imap.api.process;

/**
 * Represents well-known mailbox types along with their string representations
 * used by XLIST command
 */
public enum MailboxType {

    INBOX("\\Inbox", null),
    DRAFTS("\\Drafts", "\\Drafts"),
    TRASH("\\Trash", "\\Trash"),
    SPAM("\\Spam", "\\Junk"),
    SENT("\\Sent", "\\Sent"),
    STARRED("\\Starred", "\\Flagged"),
    ALLMAIL("\\AllMail", "\\All"),
    ARCHIVE(null, "\\Archive"),
    OTHER(null, null);

    private final String attributeName;
    private final String rfc6154attributeName;

    MailboxType(String attributeName, String rfc6154attributeName) {
        this.attributeName = attributeName;
        this.rfc6154attributeName = rfc6154attributeName;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public String getRfc6154attributeName() {
        return rfc6154attributeName;
    }
}
