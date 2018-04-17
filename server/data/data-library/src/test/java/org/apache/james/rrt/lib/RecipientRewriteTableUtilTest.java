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
import org.junit.Test;

public class RecipientRewriteTableUtilTest {

    @Test
    public void regexMapShouldCorrectlyReplaceMatchingUsername() throws Exception {
        MailAddress mailAddress = new MailAddress("prefix_abc@test");
        assertThat(RecipientRewriteTableUtil.regexMap(mailAddress, MappingImpl.regex("prefix_.*:admin@test")))
            .isEqualTo("admin@test");
    }

    @Test
    public void regexMapShouldThrowOnNullAddress() throws Exception {
        MailAddress address = null;
        assertThatThrownBy(() -> RecipientRewriteTableUtil.regexMap(address, MappingImpl.regex("prefix_.*:admin@test")))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void regexMapShouldThrowOnNullRegexMapping() throws Exception {
        Mapping regexMapping = null;
        assertThatThrownBy(() -> RecipientRewriteTableUtil.regexMap(new MailAddress("abc@test"), regexMapping))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void regexMapShouldThrowOnNonRegexMapping() throws Exception {
        assertThatThrownBy(() -> RecipientRewriteTableUtil.regexMap(new MailAddress("abc@test"), MappingImpl.error("mapping")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void regexMapShouldThrowOnInvalidSyntax() throws Exception {
        assertThatThrownBy(() -> RecipientRewriteTableUtil.regexMap(new MailAddress("abc@test"), MappingImpl.regex("singlepart")))
            .isInstanceOf(PatternSyntaxException.class);
    }

    @Test
    public void regexMapShouldReturnInputWhenRegexDoesntMatch() throws Exception {
        assertThat(RecipientRewriteTableUtil.regexMap(new MailAddress("abc@test"), MappingImpl.regex("notmatching:notreplaced")))
            .isNull();
    }

    @Test
    public void regexMapShouldCorrectlyReplaceMatchingGroups() throws Exception {
        MailAddress mailAddress = new MailAddress("prefix_abc@test");
        assertThat(RecipientRewriteTableUtil.regexMap(mailAddress, MappingImpl.regex("prefix_(.*)@test:admin@${1}")))
            .isEqualTo("admin@abc");
    }

    @Test
    public void regexMapShouldCorrectlyReplaceSeveralMatchingGroups() throws Exception {
        MailAddress mailAddress = new MailAddress("prefix_abc_def@test");
        assertThat(RecipientRewriteTableUtil.regexMap(mailAddress, MappingImpl.regex("prefix_(.*)_(.*)@test:admin@${1}.${2}")))
            .isEqualTo("admin@abc.def");
    }

    @Test
    public void getSeparatorShouldReturnCommaWhenCommaIsPresent() {
        String separator = RecipientRewriteTableUtil.getSeparator("regex:(.*)@localhost, regex:user@test");
        assertThat(separator).isEqualTo(",");
    }

    @Test
    public void getSeparatorShouldReturnEmptyWhenColonIsPresentInPrefix() {
        String separator = RecipientRewriteTableUtil.getSeparator("regex:(.*)@localhost");
        assertThat(separator).isEqualTo("");
    }

    @Test
    public void getSeparatorShouldReturnEmptyWhenColonIsPresent() {
        String separator = RecipientRewriteTableUtil.getSeparator("(.*)@localhost: user@test");
        assertThat(separator).isEqualTo(":");
    }

    @Test
    public void getSeparatorShouldReturnColonWhenNoSeparator() {
        String separator = RecipientRewriteTableUtil.getSeparator("user@test");
        assertThat(separator).isEqualTo(":");
    }
}