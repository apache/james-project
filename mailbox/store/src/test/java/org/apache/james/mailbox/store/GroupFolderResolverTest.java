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
package org.apache.james.mailbox.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;

import org.apache.james.mailbox.MailboxSession.SessionType;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.junit.Test;
import org.slf4j.Logger;

public class GroupFolderResolverTest {

    private static final long UID_VALIDITY = 9999;
    private Logger log = null;
    private List<Locale> localePreferences = null;
    private List<String> sharedSpaces = null;
    private char pathSeparator = ' ';
    
    @Test
    public void isGroupFolderShouldReturnFalseWhenMailboxNamespaceIsNull() {
        SimpleMailboxSession mailboxSession = new SimpleMailboxSession(1, "username", "password", log, localePreferences, sharedSpaces, null, pathSeparator, SessionType.User);
        GroupFolderResolver testee =  new GroupFolderResolver(mailboxSession);
        
        SimpleMailbox<TestId> mailbox = new SimpleMailbox<TestId>(new MailboxPath(null, "user", "name"), UID_VALIDITY);
        assertThat(testee.isGroupFolder(mailbox)).isFalse();
    }
    
    @Test
    public void isGroupFolderShouldReturnFalseWhenMailboxNamespaceEqualsToUserNamespace() {
        SimpleMailboxSession mailboxSession = new SimpleMailboxSession(1, "username", "password", log, localePreferences, sharedSpaces, null, pathSeparator, SessionType.User);
        GroupFolderResolver testee =  new GroupFolderResolver(mailboxSession);
        
        SimpleMailbox<TestId> mailbox = new SimpleMailbox<TestId>(new MailboxPath(MailboxConstants.USER_NAMESPACE, "user", "name"), UID_VALIDITY);
        assertThat(testee.isGroupFolder(mailbox)).isFalse();
    }
    
    @Test
    public void isGroupFolderShouldReturnFalseWhenMailboxNamespaceEqualsToOtherUsersNamespace() {
        String otherUsersSpace = "other";
        SimpleMailboxSession mailboxSession = new SimpleMailboxSession(1, "username", "password", log, localePreferences, sharedSpaces, otherUsersSpace, pathSeparator, SessionType.User);
        GroupFolderResolver testee =  new GroupFolderResolver(mailboxSession);
        
        SimpleMailbox<TestId> mailbox = new SimpleMailbox<TestId>(new MailboxPath("other", "user", "name"), UID_VALIDITY);
        assertThat(testee.isGroupFolder(mailbox)).isFalse();
    }
    
    @Test
    public void isGroupFolderShouldReturnTrueWhenMailboxNamespaceDoesntEqualToOtherUsersNamespace() {
        String otherUsersSpace = "other";
        SimpleMailboxSession mailboxSession = new SimpleMailboxSession(1, "username", "password", log, localePreferences, sharedSpaces, otherUsersSpace, pathSeparator, SessionType.User);
        GroupFolderResolver testee =  new GroupFolderResolver(mailboxSession);
        
        SimpleMailbox<TestId> mailbox = new SimpleMailbox<TestId>(new MailboxPath("namespace", "user", "name"), UID_VALIDITY);
        assertThat(testee.isGroupFolder(mailbox)).isTrue();
    }
}
