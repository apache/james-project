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

package org.apache.james.imap.main;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.imap.api.ImapSessionUtils;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PathConverterTest {

    private static final String USERNAME = "username";
    private static final char PATH_DELIMITER = '.';

    private ImapSession imapSession;
    private MailboxSession mailboxSession;
    private PathConverter pathConverter;
    @Rule public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() {
        imapSession = mock(ImapSession.class);
        mailboxSession = mock(MailboxSession.class);
        MailboxSession.User user = mock(MailboxSession.User.class);
        pathConverter = PathConverter.forSession(imapSession);
        when(imapSession.getAttribute(ImapSessionUtils.MAILBOX_SESSION_ATTRIBUTE_SESSION_KEY)).thenReturn(mailboxSession);
        when(mailboxSession.getUser()).thenReturn(user);
        when(mailboxSession.getPathDelimiter()).thenReturn(PATH_DELIMITER);
        when(user.getUserName()).thenReturn(USERNAME);
    }

    @Test
    public void buildFullPathShouldAcceptNull() {
        assertThat(pathConverter.buildFullPath(null))
            .isEqualTo(new MailboxPath("", "", ""));
    }

    @Test
    public void buildPathShouldAcceptEmpty() {
        assertThat(pathConverter.buildFullPath(""))
            .isEqualTo(new MailboxPath("", "", ""));
    }

    @Test
    public void buildPathShouldAcceptRelativeMailboxName() {
        String mailboxName = "mailboxName";
        assertThat(pathConverter.buildFullPath(mailboxName))
            .isEqualTo(MailboxPath.forUser(USERNAME, mailboxName));
    }

    @Ignore("Shared mailbox is not supported yet")
    @Test
    public void buildFullPathShouldAcceptNamespacePrefix() {
        assertThat(pathConverter.buildFullPath("#"))
            .isEqualTo(new MailboxPath("#", null, ""));
    }

    @Test
    public void buildFullPathShouldAcceptUserNamespace() {
        assertThat(pathConverter.buildFullPath(MailboxConstants.USER_NAMESPACE))
            .isEqualTo(MailboxPath.forUser(USERNAME, ""));
    }

    @Ignore("Shared mailbox is not supported yet")
    @Test
    public void buildFullPathShouldAcceptNamespaceAlone() {
        String namespace = "#any";
        assertThat(pathConverter.buildFullPath(namespace))
            .isEqualTo(new MailboxPath(namespace, null, ""));
    }

    @Test
    public void buildFullPathShouldAcceptUserNamespaceAndDelimiter() {
        assertThat(pathConverter.buildFullPath(MailboxConstants.USER_NAMESPACE + PATH_DELIMITER))
            .isEqualTo(MailboxPath.forUser(USERNAME, ""));
    }

    @Ignore("Shared mailbox is not supported yet")
    @Test
    public void buildFullPathShouldAcceptNamespaceAndDelimiter() {
        String namespace = "#any";
        assertThat(pathConverter.buildFullPath(namespace + PATH_DELIMITER))
            .isEqualTo(new MailboxPath(namespace, null, ""));
    }

    @Test
    public void buildFullPathShouldAcceptFullAbsoluteUserPath() {
        String mailboxName = "mailboxName";
        assertThat(pathConverter.buildFullPath(MailboxConstants.USER_NAMESPACE + PATH_DELIMITER + mailboxName))
            .isEqualTo(MailboxPath.forUser(USERNAME, mailboxName));
    }

    @Ignore("Shared mailbox is not supported yet")
    @Test
    public void buildFullPathShouldAcceptFullAbsolutePath() {
        String namespace = "#any";
        String mailboxName = "mailboxName";
        assertThat(pathConverter.buildFullPath(namespace + PATH_DELIMITER + mailboxName))
            .isEqualTo(new MailboxPath(namespace, null, mailboxName));
    }

    @Test
    public void buildFullPathShouldAcceptRelativePathWithSubFolder() {
        String mailboxName = "mailboxName" + PATH_DELIMITER + "subFolder";
        assertThat(pathConverter.buildFullPath(mailboxName))
            .isEqualTo(MailboxPath.forUser(USERNAME, mailboxName));
    }

    @Test
    public void buildFullPathShouldAcceptAbsoluteUserPathWithSubFolder() {
        String mailboxName = "mailboxName.subFolder";
        assertThat(pathConverter.buildFullPath(MailboxConstants.USER_NAMESPACE + PATH_DELIMITER + mailboxName))
            .isEqualTo(MailboxPath.forUser(USERNAME, mailboxName));
    }

    @Ignore("Shared mailbox is not supported yet")
    @Test
    public void buildFullPathShouldAcceptAbsolutePathWithSubFolder() {
        String namespace = "#any";
        String mailboxName = "mailboxName.subFolder";
        assertThat(pathConverter.buildFullPath(namespace + PATH_DELIMITER + mailboxName))
            .isEqualTo(new MailboxPath(namespace, null, mailboxName));
    }

    @Test
    public void buildFullPathShouldDenyMailboxPathNotBelongingToTheUser() {
        expectedException.expect(DeniedAccessOnSharedMailboxException.class);
        pathConverter.buildFullPath("#any");
    }
}
