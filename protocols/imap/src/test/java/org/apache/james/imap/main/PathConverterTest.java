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
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PathConverterTest {

    private static final Username USERNAME = Username.of("username");
    private static final Username USERNAME_WITH_DOT = Username.of("username.with.dot");
    private static final Username USERNAME_WITH_UNDERSCORE = Username.of("username_with_underscore");
    private static final Username USERNAME2 = Username.of("username2");

    private static final Username USERNAME_WITH_MAIL = Username.of("username@apache.org");
    private static final Username USERNAME2_WITH_MAIL = Username.of("username2@apache.org");
    private static final char PATH_DELIMITER = '.';
    public static final boolean RELATIVE = true;

    private MailboxSession mailboxSession;
    private PathConverter pathConverter;

    @BeforeEach
    void setUp() {
        mailboxSession = MailboxSessionUtil.create(USERNAME);
        pathConverter = PathConverter.Factory.DEFAULT.forSession(mailboxSession);
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
    void buildFullPathShouldAcceptAbsoluteOtherUserPath() {
        assertThat(pathConverter.buildFullPath("#user.username2.abc"))
            .isEqualTo(MailboxPath.forUser(USERNAME2, "abc"));
    }

    @Test
    void buildFullPathShouldAcceptAbsoluteOtherUserPathWithDot() {
        assertThat(pathConverter.buildFullPath("#user.username__with__dot.abc"))
            .isEqualTo(MailboxPath.forUser(USERNAME_WITH_DOT, "abc"));
    }

    @Test
    void buildFullPathShouldAcceptAbsoluteOtherUserPathWithUnderscore() {
        assertThat(pathConverter.buildFullPath("#user.username_-with_-underscore.abc"))
            .isEqualTo(MailboxPath.forUser(USERNAME_WITH_UNDERSCORE, "abc"));
    }

    @Test
    void buildFullPathShouldAcceptAbsoluteOtherUserPathWithSubfolder() {
        assertThat(pathConverter.buildFullPath("#user.username2.abc.def"))
            .isEqualTo(MailboxPath.forUser(USERNAME2, "abc.def"));
    }

    @Test
    void buildFullPathShouldDenyMailboxPathNotBelongingToTheUser() {
        assertThatThrownBy(() -> pathConverter.buildFullPath("#any"))
            .isInstanceOf(DeniedAccessOnSharedMailboxException.class);
    }

    @Test
    void mailboxNameShouldReturnNameOnlyWhenRelativeAndUserMailbox() {
        assertThat(pathConverter.mailboxName(RELATIVE, MailboxPath.forUser(USERNAME, "abc"), mailboxSession))
            .contains("abc");
    }

    @Test
    void mailboxNameShouldReturnFQDNWhenRelativeAndOtherUserMailbox() {
        assertThat(pathConverter.mailboxName(RELATIVE, MailboxPath.forUser(USERNAME2, "abc"), mailboxSession))
            .contains("#user.username2.abc");
    }

    @Test
    void mailboxNameShouldEscapeDotInUsername() {
        assertThat(pathConverter.mailboxName(RELATIVE, MailboxPath.forUser(USERNAME_WITH_DOT, "abc"), mailboxSession))
            .contains("#user.username__with__dot.abc");
    }

    @Test
    void mailboxNameShouldEscapeUnderscoreInUsername() {
        assertThat(pathConverter.mailboxName(RELATIVE, MailboxPath.forUser(USERNAME_WITH_UNDERSCORE, "abc"), mailboxSession))
            .contains("#user.username_-with_-underscore.abc");
    }

    @Test
    void mailboxNameShouldReturnFQDNWhenRelativeAndSharedMailbox() {
        assertThat(pathConverter.mailboxName(RELATIVE, new MailboxPath("#Shared", Username.of("marketing"), "abc"), mailboxSession))
            .contains("#Shared.marketing.abc");
    }

    @Test
    void mailboxNameShouldReturnFQDNWhenNotRelativeAndUserMailbox() {
        assertThat(pathConverter.mailboxName(!RELATIVE, MailboxPath.forUser(USERNAME, "abc"), mailboxSession))
            .contains("#private.abc");
    }

    @Test
    void mailboxNameShouldReturnFQDNWhenNotRelativeAndOtherUserMailbox() {
        assertThat(pathConverter.mailboxName(!RELATIVE, MailboxPath.forUser(USERNAME2, "abc"), mailboxSession))
            .contains("#user.username2.abc");
    }

    @Test
    void mailboxNameShouldReturnFQDNWhenNotRelativeAndSharedMailbox() {
        assertThat(pathConverter.mailboxName(!RELATIVE, new MailboxPath("#Shared", Username.of("marketing"), "abc"), mailboxSession))
            .contains("#Shared.marketing.abc");
    }
    
    @Nested
    class WithEmail {
        @BeforeEach
        void setUp() {
            mailboxSession = MailboxSessionUtil.create(USERNAME_WITH_MAIL);
            pathConverter = PathConverter.Factory.DEFAULT.forSession(mailboxSession);
        }

        @Test
        void buildFullPathShouldAcceptNull() {
            assertThat(pathConverter.buildFullPath(null))
                .isEqualTo(new MailboxPath("", USERNAME_WITH_MAIL, ""));
        }

        @Test
        void buildPathShouldAcceptEmpty() {
            assertThat(pathConverter.buildFullPath(""))
                .isEqualTo(new MailboxPath("", USERNAME_WITH_MAIL, ""));
        }

        @Test
        void buildPathShouldAcceptRelativeMailboxName() {
            String mailboxName = "mailboxName";
            assertThat(pathConverter.buildFullPath(mailboxName))
                .isEqualTo(MailboxPath.forUser(USERNAME_WITH_MAIL, mailboxName));
        }

        @Test
        void buildFullPathShouldAcceptUserNamespace() {
            assertThat(pathConverter.buildFullPath(MailboxConstants.USER_NAMESPACE))
                .isEqualTo(MailboxPath.forUser(USERNAME_WITH_MAIL, ""));
        }

        @Test
        void buildFullPathShouldAcceptUserNamespaceAndDelimiter() {
            assertThat(pathConverter.buildFullPath(MailboxConstants.USER_NAMESPACE + PATH_DELIMITER))
                .isEqualTo(MailboxPath.forUser(USERNAME_WITH_MAIL, ""));
        }

        @Test
        void buildFullPathShouldAcceptFullAbsoluteUserPath() {
            String mailboxName = "mailboxName";
            assertThat(pathConverter.buildFullPath(MailboxConstants.USER_NAMESPACE + PATH_DELIMITER + mailboxName))
                .isEqualTo(MailboxPath.forUser(USERNAME_WITH_MAIL, mailboxName));
        }

        @Test
        void buildFullPathShouldAcceptRelativePathWithSubFolder() {
            String mailboxName = "mailboxName" + PATH_DELIMITER + "subFolder";
            assertThat(pathConverter.buildFullPath(mailboxName))
                .isEqualTo(MailboxPath.forUser(USERNAME_WITH_MAIL, mailboxName));
        }

        @Test
        void buildFullPathShouldAcceptAbsoluteUserPathWithSubFolder() {
            String mailboxName = "mailboxName.subFolder";
            assertThat(pathConverter.buildFullPath(MailboxConstants.USER_NAMESPACE + PATH_DELIMITER + mailboxName))
                .isEqualTo(MailboxPath.forUser(USERNAME_WITH_MAIL, mailboxName));
        }

        @Test
        void buildFullPathShouldAcceptAbsoluteOtherUserPath() {
            assertThat(pathConverter.buildFullPath("#user.username2.abc"))
                .isEqualTo(MailboxPath.forUser(USERNAME2_WITH_MAIL, "abc"));
        }

        @Test
        void buildFullPathShouldAcceptAbsoluteOtherUserPathWithSubfolder() {
            assertThat(pathConverter.buildFullPath("#user.username2.abc.def"))
                .isEqualTo(MailboxPath.forUser(USERNAME2_WITH_MAIL, "abc.def"));
        }

        @Test
        void mailboxNameShouldReturnNameOnlyWhenRelativeAndUserMailbox() {
            assertThat(pathConverter.mailboxName(RELATIVE, MailboxPath.forUser(USERNAME_WITH_MAIL, "abc"), mailboxSession))
                .contains("abc");
        }

        @Test
        void mailboxNameShouldReturnFQDNWhenRelativeAndOtherUserMailbox() {
            assertThat(pathConverter.mailboxName(RELATIVE, MailboxPath.forUser(USERNAME2_WITH_MAIL, "abc"), mailboxSession))
                .contains("#user.username2.abc");
        }

        @Test
        void mailboxNameShouldReturnFQDNWhenRelativeAndSharedMailbox() {
            assertThat(pathConverter.mailboxName(RELATIVE, new MailboxPath("#Shared", Username.of("marketing@apache.org"), "abc"), mailboxSession))
                .contains("#Shared.marketing.abc");
        }

        @Test
        void mailboxNameShouldReturnFQDNWhenNotRelativeAndUserMailbox() {
            assertThat(pathConverter.mailboxName(!RELATIVE, MailboxPath.forUser(USERNAME_WITH_MAIL, "abc"), mailboxSession))
                .contains("#private.abc");
        }

        @Test
        void mailboxNameShouldReturnFQDNWhenNotRelativeAndOtherUserMailbox() {
            assertThat(pathConverter.mailboxName(!RELATIVE, MailboxPath.forUser(USERNAME2_WITH_MAIL, "abc"), mailboxSession))
                .contains("#user.username2.abc");
        }

        @Test
        void mailboxNameShouldReturnFQDNWhenNotRelativeAndSharedMailbox() {
            assertThat(pathConverter.mailboxName(!RELATIVE, new MailboxPath("#Shared", Username.of("marketing@apache.org"), "abc"), mailboxSession))
                .contains("#Shared.marketing.abc");
        }
    }
}
