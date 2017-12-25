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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.junit.Before;
import org.junit.Test;

public class ImapSessionUtilsTest {
    private static final String USERNAME = "username";
    private static final MailboxSession MAILBOX_SESSION = new MockMailboxSession(USERNAME);
    private FakeImapSession fakeImapSession;

    @Before
    public void setUp() {
        fakeImapSession = new FakeImapSession();
    }

    @Test
    public void getUserNameShouldReturnNullWhenNoMailboxSession() {
        assertThat(ImapSessionUtils.getUserName(fakeImapSession))
            .isNull();
    }

    @Test
    public void getUserNameShouldReturnUserWhenMailboxSession() {
        fakeImapSession.setAttribute(ImapSessionUtils.MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY, MAILBOX_SESSION);
        assertThat(ImapSessionUtils.getUserName(fakeImapSession))
            .isEqualTo(USERNAME);
    }

    @Test
    public void getUserNameShouldThrowOnNullImapSession() {
        assertThatThrownBy(() -> ImapSessionUtils.getUserName(null))
            .isInstanceOf(NullPointerException.class);
    }

}