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
package org.apache.james.protocols.smtp.core.esmtp;

import java.util.Optional;

import org.apache.james.core.Username;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class AuthCmdHandlerTest {

    @Test
    void shouldReturnEmptyWhenEmptyInput() {
        Assertions.assertThat(AuthCmdHandler.parseAuthValues("")).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenBlankInput() {
        Assertions.assertThat(AuthCmdHandler.parseAuthValues(" \t\n")).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenTwoBlankParts() {
        Assertions.assertThat(AuthCmdHandler.parseAuthValues(" \0\t\n")).isEmpty();
    }

    @Test
    void shouldReturnEmptyWhenThreeBlankParts() {
        Assertions.assertThat(AuthCmdHandler.parseAuthValues(" \0\0 ")).isEmpty();
    }

    @Test
    void shouldReturnUsernameWhenSinglePart() {
        Assertions.assertThat(AuthCmdHandler.parseAuthValues("bob"))
                .contains(new AuthCmdHandler.AuthValues(Username.of("bob"), Optional.empty()));
    }

    @Test
    void shouldReturnUsernameAndPassWhenTwoParts() {
        Assertions.assertThat(AuthCmdHandler.parseAuthValues("bob\0pass"))
                .contains(new AuthCmdHandler.AuthValues(Username.of("bob"), Optional.of("pass")));
    }

    @Test
    void shouldReturnUsernameAndPassWhenThreeParts() {
        Assertions.assertThat(AuthCmdHandler.parseAuthValues("something\0bob\0pass"))
                .contains(new AuthCmdHandler.AuthValues(Username.of("bob"), Optional.of("pass")));
    }


}