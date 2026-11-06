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

package org.apache.james.rrt.lib;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.regex.PatternSyntaxException;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.junit.jupiter.api.Test;

class RegexRewriterTest {
    
    @Test
    void regexMapShouldCorrectlyReplaceMatchingUsername() throws Exception {
        MailAddress mailAddress = new MailAddress("prefix_abc@test");
        assertThat(new UserRewritter.RegexRewriter().regexMap(mailAddress,"prefix_.*:admin@test"))
            .contains("admin@test");
    }

    @Test
    void regexMapShouldThrowOnNullAddress() throws Exception {
        MailAddress address = null;
        assertThatThrownBy(() -> new UserRewritter.RegexRewriter().regexMap(address, "prefix_.*:admin@test"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void regexMapShouldThrowOnNullRegexMapping() throws Exception {
        String regexMapping = null;
        assertThatThrownBy(() -> new UserRewritter.RegexRewriter().regexMap(new MailAddress("abc@test"), regexMapping))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void regexMapShouldThrowOnInvalidSyntax() throws Exception {
        assertThatThrownBy(() -> new UserRewritter.RegexRewriter().regexMap(new MailAddress("abc@test"), "singlepart"))
            .isInstanceOf(PatternSyntaxException.class);
    }

    @Test
    void regexMapShouldReturnInputWhenRegexDoesntMatch() throws Exception {
        assertThat(new UserRewritter.RegexRewriter().regexMap(new MailAddress("abc@test"), "notmatching:notreplaced"))
            .isEmpty();
    }

    @Test
    void regexMapShouldCorrectlyReplaceMatchingGroups() throws Exception {
        MailAddress mailAddress = new MailAddress("prefix_abc@test");
        assertThat(new UserRewritter.RegexRewriter(RegexEngine.UNSAFE).regexMap(mailAddress, "prefix_(.*)@test:admin@${1}"))
            .contains("admin@abc");
    }

    @Test
    void regexMapShouldCorrectlyReplaceSeveralMatchingGroups() throws Exception {
        MailAddress mailAddress = new MailAddress("prefix_abc_def@test");
        assertThat(new UserRewritter.RegexRewriter(RegexEngine.UNSAFE).regexMap(mailAddress, "prefix_(.*)_(.*)@test:admin@${1}.${2}"))
            .contains("admin@abc.def");
    }

    @Test
    void regexMapShouldCorrectlyReplaceSeveralOutOfOrderMatchingGroups() throws Exception {
        MailAddress mailAddress = new MailAddress("prefix_abc_def@test");
        assertThat(new UserRewritter.RegexRewriter(RegexEngine.UNSAFE).regexMap(mailAddress, "prefix_(.*)_(.*)@test:admin@${2}.${1}"))
            .contains("admin@def.abc");
    }


    @Test
    void regexMapShouldNotBacktrackCatastrophically() throws Exception {
        MailAddress mailAddress = new MailAddress("a".repeat(64) + "!@test");
        assertTimeoutPreemptively(Duration.ofSeconds(10),
            () -> assertThat(new UserRewritter.RegexRewriter().regexMap(mailAddress, "(a+)+@test:admin@test")).isEmpty());
    }

    @Test
    void regexMapShouldThrowOnParameters() throws Exception {
        assertThatThrownBy(() -> new UserRewritter.RegexRewriter().regexMap(new MailAddress("prefix_abc@test"), "prefix_(.*)@test:admin@${1}"))
            .isInstanceOf(PatternSyntaxException.class);
    }

    @Test
    void userRewriterShouldNotRewriteWhenParameters() throws Exception {
        assertThat(new UserRewritter.RegexRewriter().generateUserRewriter("prefix_(.*)@test:admin@${1}")
            .rewrite(Username.of("prefix_abc@test")))
            .isEmpty();
    }

    @Test
    void regexMapShouldThrowOnBraces() throws Exception {
        assertThatThrownBy(() -> new UserRewritter.RegexRewriter().regexMap(new MailAddress("abc@test"), "((a{1000}){1000}){1000}:admin@test"))
            .isInstanceOf(PatternSyntaxException.class);
    }

    @Test
    void regexMapShouldCorrectlyReplaceRepeatingMatchingGroups() throws Exception {
        MailAddress mailAddress = new MailAddress("prefix_abc_def@test");
        assertThat(new UserRewritter.RegexRewriter(RegexEngine.UNSAFE).regexMap(mailAddress, "prefix_(.*)_(.*)@test:admin@${1}.${1}"))
            .contains("admin@abc.abc");
    }
}
