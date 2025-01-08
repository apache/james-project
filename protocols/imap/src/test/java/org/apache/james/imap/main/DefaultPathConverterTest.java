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

import org.apache.james.mailbox.model.MailboxConstants;
import org.junit.jupiter.api.Nested;

public class DefaultPathConverterTest {
    @Nested
    public class DotDelimiter extends TestBase {
        @Override
        public char folderDelimiter() {
            return MailboxConstants.MailboxFolderDelimiter.DOT.value;
        }
    }

    @Nested
    public class SlashDelimiter extends TestBase {
        @Override
        public char folderDelimiter() {
            return MailboxConstants.MailboxFolderDelimiter.SLASH.value;
        }
    }

    @Nested
    public class PipeDelimiter extends TestBase {
        @Override
        public char folderDelimiter() {
            return MailboxConstants.MailboxFolderDelimiter.PIPE.value;
        }
    }

    @Nested
    public class CommaDelimiter extends TestBase {
        @Override
        public char folderDelimiter() {
            return MailboxConstants.MailboxFolderDelimiter.COMMA.value;
        }
    }

    @Nested
    public class ColonDelimiter extends TestBase {
        @Override
        public char folderDelimiter() {
            return MailboxConstants.MailboxFolderDelimiter.COLON.value;
        }
    }

    @Nested
    public class SemicolonDelimiter extends TestBase {
        @Override
        public char folderDelimiter() {
            return MailboxConstants.MailboxFolderDelimiter.SEMICOLON.value;
        }
    }

    public abstract static class TestBase extends PathConverterBasicContract {
        private final PathConverter pathConverter = PathConverter.Factory.DEFAULT.forSession(mailboxSession);

        @Override
        public PathConverter pathConverter() {
            return pathConverter;
        }

        @Nested
        public class WithEmail extends PathConverterBasicContract.WithEmail {
            private final PathConverter pathConverter = PathConverter.Factory.DEFAULT.forSession(mailboxWithEmailSession);

            @Override
            public PathConverter pathConverter() {
                return pathConverter;
            }
        }
    }
}
