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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.core.Username;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PathConverterTest {

    private static final Username USERNAME = Username.of("username");
    private static final char PATH_DELIMITER = '.';

    private FakeImapSession imapSession;
    private MailboxSession mailboxSession;
    private PathConverter pathConverter;

    @BeforeEach
    void setUp() {
        imapSession = new FakeImapSession();
        mailboxSession = MailboxSessionUtil.create(USERNAME);
        pathConverter = PathConverter.forSession(imapSession);
        imapSession.setMailboxSession(mailboxSession);
    }

    @Test
    void buildFullPathShouldAcceptNull() {
        assertThat(pathConverter.buildFullPath(null))
            .isEqualTo(new MailboxPath("", USERNAME, ""));
    }

    @Test
    void buildPathShouldAcceptEmpty() {
        assertThat(pathConverter.buildFullPath(""))
            .isEqualTo(new MailboxPath("", USERNAME, ""));
    }

    @Test
    void buildPathShouldAcceptRelativeMailboxName() {
        String mailboxName = "mailboxName";
        assertThat(pathConverter.buildFullPath(mailboxName))
            .isEqualTo(MailboxPath.forUser(USERNAME, mailboxName));
    }

    @Test
    void buildFullPathShouldAcceptUserNamespace() {
        assertThat(pathConverter.buildFullPath(MailboxConstants.USER_NAMESPACE))
            .isEqualTo(MailboxPath.forUser(USERNAME, ""));
    }

    @Test
    void buildFullPathShouldAcceptUserNamespaceAndDelimiter() {
        assertThat(pathConverter.buildFullPath(MailboxConstants.USER_NAMESPACE + PATH_DELIMITER))
            .isEqualTo(MailboxPath.forUser(USERNAME, ""));
    }

    @Test
    void buildFullPathShouldAcceptFullAbsoluteUserPath() {
        String mailboxName = "mailboxName";
        assertThat(pathConverter.buildFullPath(MailboxConstants.USER_NAMESPACE + PATH_DELIMITER + mailboxName))
            .isEqualTo(MailboxPath.forUser(USERNAME, mailboxName));
    }

    @Test
    void buildFullPathShouldAcceptRelativePathWithSubFolder() {
        String mailboxName = "mailboxName" + PATH_DELIMITER + "subFolder";
        assertThat(pathConverter.buildFullPath(mailboxName))
            .isEqualTo(MailboxPath.forUser(USERNAME, mailboxName));
    }

    @Test
    void buildFullPathShouldAcceptAbsoluteUserPathWithSubFolder() {
        String mailboxName = "mailboxName.subFolder";
        assertThat(pathConverter.buildFullPath(MailboxConstants.USER_NAMESPACE + PATH_DELIMITER + mailboxName))
            .isEqualTo(MailboxPath.forUser(USERNAME, mailboxName));
    }

    @Test
    void buildFullPathShouldDenyMailboxPathNotBelongingToTheUser() {
        assertThatThrownBy(() -> pathConverter.buildFullPath("#any"))
            .isInstanceOf(DeniedAccessOnSharedMailboxException.class);
    }
}
