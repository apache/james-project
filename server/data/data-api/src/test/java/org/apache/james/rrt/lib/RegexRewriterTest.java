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

import java.util.regex.PatternSyntaxException;

import org.apache.james.core.MailAddress;
import org.junit.jupiter.api.Test;

public class RegexRewriterTest {
    @Test
    public void regexMapShouldCorrectlyReplaceMatchingUsername() throws Exception {
        MailAddress mailAddress = new MailAddress("prefix_abc@test");
        assertThat(new UserRewritter.RegexRewriter().regexMap(mailAddress,"prefix_.*:admin@test"))
            .contains("admin@test");
    }

    @Test
    public void regexMapShouldThrowOnNullAddress() throws Exception {
        MailAddress address = null;
        assertThatThrownBy(() -> new UserRewritter.RegexRewriter().regexMap(address, "prefix_.*:admin@test"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void regexMapShouldThrowOnNullRegexMapping() throws Exception {
        String regexMapping = null;
        assertThatThrownBy(() -> new UserRewritter.RegexRewriter().regexMap(new MailAddress("abc@test"), regexMapping))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void regexMapShouldThrowOnInvalidSyntax() throws Exception {
        assertThatThrownBy(() -> new UserRewritter.RegexRewriter().regexMap(new MailAddress("abc@test"), "singlepart"))
            .isInstanceOf(PatternSyntaxException.class);
    }

    @Test
    public void regexMapShouldReturnInputWhenRegexDoesntMatch() throws Exception {
        assertThat(new UserRewritter.RegexRewriter().regexMap(new MailAddress("abc@test"), "notmatching:notreplaced"))
            .isEmpty();
    }

    @Test
    public void regexMapShouldCorrectlyReplaceMatchingGroups() throws Exception {
        MailAddress mailAddress = new MailAddress("prefix_abc@test");
        assertThat(new UserRewritter.RegexRewriter().regexMap(mailAddress, "prefix_(.*)@test:admin@${1}"))
            .contains("admin@abc");
    }

    @Test
    public void regexMapShouldCorrectlyReplaceSeveralMatchingGroups() throws Exception {
        MailAddress mailAddress = new MailAddress("prefix_abc_def@test");
        assertThat(new UserRewritter.RegexRewriter().regexMap(mailAddress, "prefix_(.*)_(.*)@test:admin@${1}.${2}"))
            .contains("admin@abc.def");
    }
}
