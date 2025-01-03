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

import org.junit.jupiter.api.Nested;

public class DefaultPathConverterTest {
    @Nested
    public class DotDelimiter extends TestBase {
        @Override
        public char pathDelimiter() {
            return '.';
        }
    }

    @Nested
    public class SlashDelimiter extends TestBase {
        @Override
        public char pathDelimiter() {
            return '/';
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
