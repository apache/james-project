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

package org.apache.james.imap.api;

/**
 * Enumerates 
 * <a href='http://james.apache.org/server/rfclist/imap4/rfc2060.txt' rel='tag'>RFC2060</a>
 * session states.
 */
public enum ImapSessionState {
    /** 
     * <a href='http://james.apache.org/server/rfclist/imap4/rfc2060.txt' rel='tag'>RFC2060</a>
     * <code>3.1 Non-Authenticated State</code>
     */
    NON_AUTHENTICATED("Non Authenticated State"),
    /** 
     * <a href='http://james.apache.org/server/rfclist/imap4/rfc2060.txt' rel='tag'>RFC2060</a>
     * <code>3.2 Authenticated State</code>
     */
    AUTHENTICATED("Authenticated State"),
    /** 
     * <a href='http://james.apache.org/server/rfclist/imap4/rfc2060.txt' rel='tag'>RFC2060</a>
     * <code>3.3 Selected State</code>
     */
    SELECTED("Selected State"),
    /** 
     * <a href='http://james.apache.org/server/rfclist/imap4/rfc2060.txt' rel='tag'>RFC2060</a>
     * <code>3.4 Logout State</code>
     */
    LOGOUT("Logged Out State");

    /** To aid debugging */
    private final String name;

    private ImapSessionState(String name) {
        this.name = name;
    }

    public String toString() {
        return name;
    }
}
