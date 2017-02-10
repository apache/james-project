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

package org.apache.james.mpt.imapmailbox.suite;

import java.util.Locale;

import javax.inject.Inject;

import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mpt.api.ImapHostSystem;
import org.apache.james.mpt.imapmailbox.suite.base.BaseNonAuthenticatedState;
import org.junit.Test;

public class AuthenticatePlain extends BaseNonAuthenticatedState {
    
    @Inject
    private static ImapHostSystem system;

    public AuthenticatePlain() throws Exception {
        super(system);
    }

    @Test
    public void testAuthenticatePlainUS() throws Exception {
        system.addUser("delegate", "123456");
        system.createMailbox(new MailboxPath(MailboxConstants.USER_NAMESPACE, "delegate", "delegate"));
        system.createMailbox(new MailboxPath(MailboxConstants.USER_NAMESPACE, "imapuser", "imapuser"));
        scriptTest("AuthenticatePlain", Locale.US);
    }

    @Test
    public void testAuthenticatePlainITALY() throws Exception {
        system.addUser("delegate", "123456");
        system.createMailbox(new MailboxPath(MailboxConstants.USER_NAMESPACE, "delegate", "delegate"));
        system.createMailbox(new MailboxPath(MailboxConstants.USER_NAMESPACE, "imapuser", "imapuser"));
        scriptTest("AuthenticatePlain", Locale.ITALY);
    }

    @Test
    public void testAuthenticatePlainKOREA() throws Exception {
        system.addUser("delegate", "123456");
        system.createMailbox(new MailboxPath(MailboxConstants.USER_NAMESPACE, "delegate", "delegate"));
        system.createMailbox(new MailboxPath(MailboxConstants.USER_NAMESPACE, "imapuser", "imapuser"));
        scriptTest("AuthenticatePlain", Locale.KOREA);
    }
}
