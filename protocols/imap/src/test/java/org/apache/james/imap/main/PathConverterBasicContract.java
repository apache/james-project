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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public abstract class PathConverterBasicContract {
    public static final Username USERNAME = Username.of("username");
    public static final Username USERNAME_WITH_DELIMITER = Username.of("username.with.delimiter");
    public static final Username USERNAME_WITH_UNDERSCORE = Username.of("username_with_underscore");
    public static final Username USERNAME2 = Username.of("username2");

    public static final Username USERNAME_WITH_MAIL = Username.of("username@apache.org");
    public static final Username USERNAME2_WITH_MAIL = Username.of("username2@apache.org");
    public static final boolean RELATIVE = true;

    public final MailboxSession mailboxSession = MailboxSessionUtil.create(USERNAME, pathDelimiter());

    abstract PathConverter pathConverter();

    abstract char pathDelimiter();

    static char initialPathDelimiter;

    @BeforeEach
    public void setUp() {
        initialPathDelimiter = MailboxConstants.FOLDER_DELIMITER;
        MailboxConstants.FOLDER_DELIMITER = pathDelimiter();
    }

    @AfterEach
    public void tearDown() {
        MailboxConstants.FOLDER_DELIMITER = initialPathDelimiter;
    }

    static String adjustToTestDelimiter(String valueWithDots) {
        // Because the test setup will configure the desired delimiter to be used,
        // we do not need to pass it in manually here.
        return valueWithDots.replace('.', MailboxConstants.FOLDER_DELIMITER);
    }

    static Username adjustToTestDelimiter(Username username) {
        return Username.of(adjustToTestDelimiter(username.asString()));
    }

    @Test
    public void buildFullPathShouldAcceptNull() {
        assertThat(pathConverter().buildFullPath(null))
            .isEqualTo(new MailboxPath("", USERNAME, ""));
    }

    @Test
    public void buildPathShouldAcceptEmpty() {
        assertThat(pathConverter().buildFullPath(""))
            .isEqualTo(new MailboxPath("", USERNAME, ""));
    }

    @Test
    public void buildPathShouldAcceptRelativeMailboxName() {
        String mailboxName = "mailboxName";
        assertThat(pathConverter().buildFullPath(mailboxName))
            .isEqualTo(MailboxPath.forUser(USERNAME, mailboxName));
    }

    @Test
    public void buildFullPathShouldAcceptUserNamespace() {
        assertThat(pathConverter().buildFullPath(MailboxConstants.USER_NAMESPACE))
            .isEqualTo(MailboxPath.forUser(USERNAME, ""));
    }

    @Test
    public void buildFullPathShouldAcceptUserNamespaceAndDelimiter() {
        assertThat(pathConverter().buildFullPath(MailboxConstants.USER_NAMESPACE + pathDelimiter()))
            .isEqualTo(MailboxPath.forUser(USERNAME, ""));
    }

    @Test
    public void buildFullPathShouldAcceptFullAbsoluteUserPath() {
        String mailboxName = "mailboxName";
        assertThat(pathConverter().buildFullPath(MailboxConstants.USER_NAMESPACE + pathDelimiter() + mailboxName))
                .isEqualTo(MailboxPath.forUser(USERNAME, mailboxName));
    }

    @Test
    public void buildFullPathShouldAcceptRelativePathWithSubFolder() {
        String mailboxName = adjustToTestDelimiter("mailboxName.subFolder");
        assertThat(pathConverter().buildFullPath(mailboxName))
                .isEqualTo(MailboxPath.forUser(USERNAME, mailboxName));
    }

    @Test
    public void buildFullPathShouldAcceptAbsoluteUserPathWithSubFolder() {
        String mailboxName = adjustToTestDelimiter("mailboxName.subFolder");
        assertThat(pathConverter().buildFullPath(MailboxConstants.USER_NAMESPACE + pathDelimiter() + mailboxName))
                .isEqualTo(MailboxPath.forUser(USERNAME, mailboxName));
    }

    @Test
    public void buildFullPathShouldAcceptAbsoluteOtherUserPath() {
        assertThat(pathConverter().buildFullPath(adjustToTestDelimiter("#user.username2.abc")))
            .isEqualTo(MailboxPath.forUser(USERNAME2, "abc"));
    }

    @Test
    public void buildFullPathShouldAcceptAbsoluteOtherUserPathWithDelimiter() {
        assertThat(pathConverter().buildFullPath(adjustToTestDelimiter("#user.username__with__delimiter.abc")))
            .isEqualTo(MailboxPath.forUser(adjustToTestDelimiter(USERNAME_WITH_DELIMITER), "abc"));
    }

    @Test
    public void buildFullPathShouldAcceptAbsoluteOtherUserPathWithUnderscore() {
        assertThat(pathConverter().buildFullPath(adjustToTestDelimiter("#user.username_-with_-underscore.abc")))
            .isEqualTo(MailboxPath.forUser(USERNAME_WITH_UNDERSCORE, "abc"));
    }

    @Test
    public void buildFullPathShouldAcceptAbsoluteOtherUserPathWithSubfolder() {
        assertThat(pathConverter().buildFullPath(adjustToTestDelimiter("#user.username2.abc.def")))
            .isEqualTo(MailboxPath.forUser(USERNAME2, adjustToTestDelimiter("abc.def")));
    }

    @Test
    public void buildFullPathShouldDenyMailboxPathNotBelongingToTheUser() {
        assertThatThrownBy(() -> pathConverter().buildFullPath("#any"))
            .isInstanceOf(DeniedAccessOnSharedMailboxException.class);
    }

    @Test
    public void mailboxNameShouldReturnNameOnlyWhenRelativeAndUserMailbox() {
        assertThat(pathConverter().mailboxName(RELATIVE, MailboxPath.forUser(USERNAME, "abc"), mailboxSession))
            .contains("abc");
    }

    @Test
    public void mailboxNameShouldReturnFQDNWhenRelativeAndOtherUserMailbox() {
        assertThat(pathConverter().mailboxName(RELATIVE, MailboxPath.forUser(USERNAME2, "abc"), mailboxSession))
            .contains(adjustToTestDelimiter("#user.username2.abc"));
    }

    @Test
    public void mailboxNameShouldEscapeDelimiterInUsername() {
        assertThat(pathConverter().mailboxName(RELATIVE, MailboxPath.forUser(adjustToTestDelimiter(USERNAME_WITH_DELIMITER), "abc"), mailboxSession))
            .contains(adjustToTestDelimiter("#user.username__with__delimiter.abc"));
    }

    @Test
    public void mailboxNameShouldEscapeUnderscoreInUsername() {
        assertThat(pathConverter().mailboxName(RELATIVE, MailboxPath.forUser(USERNAME_WITH_UNDERSCORE, "abc"), mailboxSession))
            .contains(adjustToTestDelimiter("#user.username_-with_-underscore.abc"));
    }

    @Test
    public void mailboxNameShouldReturnFQDNWhenRelativeAndSharedMailbox() {
        assertThat(pathConverter().mailboxName(RELATIVE, new MailboxPath("#Shared", Username.of("marketing"), "abc"), mailboxSession))
            .contains(adjustToTestDelimiter("#Shared.marketing.abc"));
    }

    @Test
    public void mailboxNameShouldReturnFQDNWhenNotRelativeAndUserMailbox() {
        String mailboxName = adjustToTestDelimiter("#private.abc");
        assertThat(pathConverter().mailboxName(!RELATIVE, MailboxPath.forUser(USERNAME, "abc"), mailboxSession))
            .contains(mailboxName);
    }

    @Test
    public void mailboxNameShouldReturnFQDNWhenNotRelativeAndOtherUserMailbox() {
        assertThat(pathConverter().mailboxName(!RELATIVE, MailboxPath.forUser(USERNAME2, "abc"), mailboxSession))
            .contains(adjustToTestDelimiter("#user.username2.abc"));
    }

    @Test
    public void mailboxNameShouldReturnFQDNWhenNotRelativeAndSharedMailbox() {
        assertThat(pathConverter().mailboxName(!RELATIVE, new MailboxPath("#Shared", Username.of("marketing"), "abc"), mailboxSession))
            .contains(adjustToTestDelimiter("#Shared.marketing.abc"));
    }
    
    @Nested
    public abstract class WithEmail {
        public final MailboxSession mailboxWithEmailSession = MailboxSessionUtil.create(USERNAME_WITH_MAIL, pathDelimiter());

        public abstract PathConverter pathConverter();

        @Test
        public void buildFullPathShouldAcceptNull() {
            assertThat(pathConverter().buildFullPath(null))
                .isEqualTo(new MailboxPath("", USERNAME_WITH_MAIL, ""));
        }

        @Test
        public void buildPathShouldAcceptEmpty() {
            assertThat(pathConverter().buildFullPath(""))
                .isEqualTo(new MailboxPath("", USERNAME_WITH_MAIL, ""));
        }

        @Test
        public void buildPathShouldAcceptRelativeMailboxName() {
            String mailboxName = "mailboxName";
            assertThat(pathConverter().buildFullPath(mailboxName))
                .isEqualTo(MailboxPath.forUser(USERNAME_WITH_MAIL, mailboxName));
        }

        @Test
        public void buildFullPathShouldAcceptUserNamespace() {
            assertThat(pathConverter().buildFullPath(MailboxConstants.USER_NAMESPACE))
                .isEqualTo(MailboxPath.forUser(USERNAME_WITH_MAIL, ""));
        }

        @Test
        public void buildFullPathShouldAcceptUserNamespaceAndDelimiter() {
            assertThat(pathConverter().buildFullPath(MailboxConstants.USER_NAMESPACE + pathDelimiter()))
                .isEqualTo(MailboxPath.forUser(USERNAME_WITH_MAIL, ""));
        }

        @Test
        public void buildFullPathShouldAcceptFullAbsoluteUserPath() {
            String mailboxName = "mailboxName";
            assertThat(pathConverter().buildFullPath(MailboxConstants.USER_NAMESPACE + pathDelimiter() + mailboxName))
                .isEqualTo(MailboxPath.forUser(USERNAME_WITH_MAIL, mailboxName));
        }

        @Test
        public void buildFullPathShouldAcceptRelativePathWithSubFolder() {
            String mailboxName = "mailboxName" + pathDelimiter() + "subFolder";
            assertThat(pathConverter().buildFullPath(mailboxName))
                .isEqualTo(MailboxPath.forUser(USERNAME_WITH_MAIL, mailboxName));
        }

        @Test
        public void buildFullPathShouldAcceptAbsoluteUserPathWithSubFolder() {
            String mailboxName = adjustToTestDelimiter("mailboxName.subFolder");
            assertThat(pathConverter().buildFullPath(MailboxConstants.USER_NAMESPACE + pathDelimiter() + mailboxName))
                .isEqualTo(MailboxPath.forUser(USERNAME_WITH_MAIL, mailboxName));
        }

        @Test
        public void buildFullPathShouldAcceptAbsoluteOtherUserPath() {
            assertThat(pathConverter().buildFullPath(adjustToTestDelimiter("#user.username2.abc")))
                .isEqualTo(MailboxPath.forUser(USERNAME2_WITH_MAIL, "abc"));
        }

        @Test
        public void buildFullPathShouldAcceptAbsoluteOtherUserPathWithSubfolder() {
            assertThat(pathConverter().buildFullPath(adjustToTestDelimiter("#user.username2.abc.def")))
                .isEqualTo(MailboxPath.forUser(USERNAME2_WITH_MAIL, adjustToTestDelimiter("abc.def")));
        }

        @Test
        public void mailboxNameShouldReturnNameOnlyWhenRelativeAndUserMailbox() {
            assertThat(pathConverter().mailboxName(RELATIVE, MailboxPath.forUser(USERNAME_WITH_MAIL, "abc"), mailboxWithEmailSession))
                .contains("abc");
        }

        @Test
        public void mailboxNameShouldReturnFQDNWhenRelativeAndOtherUserMailbox() {
            assertThat(pathConverter().mailboxName(RELATIVE, MailboxPath.forUser(USERNAME2_WITH_MAIL, "abc"), mailboxWithEmailSession))
                .contains(adjustToTestDelimiter("#user.username2.abc"));
        }

        @Test
        public void mailboxNameShouldReturnFQDNWhenRelativeAndSharedMailbox() {
            assertThat(pathConverter().mailboxName(RELATIVE, new MailboxPath("#Shared", Username.of("marketing@apache.org"), "abc"), mailboxWithEmailSession))
                .contains(adjustToTestDelimiter("#Shared.marketing.abc"));
        }

        @Test
        public void mailboxNameShouldReturnFQDNWhenNotRelativeAndUserMailbox() {
            assertThat(pathConverter().mailboxName(!RELATIVE, MailboxPath.forUser(USERNAME_WITH_MAIL, "abc"), mailboxWithEmailSession))
                .contains(adjustToTestDelimiter("#private.abc"));
        }

        @Test
        public void mailboxNameShouldReturnFQDNWhenNotRelativeAndOtherUserMailbox() {
            assertThat(pathConverter().mailboxName(!RELATIVE, MailboxPath.forUser(USERNAME2_WITH_MAIL, "abc"), mailboxWithEmailSession))
                .contains(adjustToTestDelimiter("#user.username2.abc"));
        }

        @Test
        public void mailboxNameShouldReturnFQDNWhenNotRelativeAndSharedMailbox() {
            assertThat(pathConverter().mailboxName(!RELATIVE, new MailboxPath("#Shared", Username.of("marketing@apache.org"), "abc"), mailboxWithEmailSession))
                .contains(adjustToTestDelimiter("#Shared.marketing.abc"));
        }
    }
}
