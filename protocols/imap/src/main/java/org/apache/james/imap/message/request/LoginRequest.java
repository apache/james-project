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
package org.apache.james.imap.message.request;

import org.apache.james.core.Username;
import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.message.request.ImapRequest;

/**
 * {@link ImapRequest} which requests the login of a user
 */
public class LoginRequest extends AbstractImapRequest {
    private final Username userid;

    private final String password;

    public LoginRequest(ImapCommand command, Username userid, String password, Tag tag) {
        super(tag, command);
        this.userid = userid;
        this.password = password;
    }

    /**
     * Return the password
     * 
     * @return pass
     */
    public final String getPassword() {
        return password;
    }

    /**
     * Return the username
     * 
     * @return user
     */
    public final Username getUserid() {
        return userid;
    }
}
