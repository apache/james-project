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

package org.apache.james.domainlist.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.Domain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import nl.jqno.equalsverifier.EqualsVerifier;

class DomainTest {
    @Test
    void shouldRespectBeanContract() {
        EqualsVerifier.forClass(Domain.class)
            .withIgnoredFields("domainName")
            .verify();
    }

    @Test
    void shouldBeCaseInsensitive() {
        assertThat(Domain.of("Domain")).isEqualTo(Domain.of("domain"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "aab..ddd",
        "aab.cc.1com",
        "abc.abcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcd.com",
        "domain$bad.com",
        "domain/bad.com",
        "domain\\bad.com",
        "domain@bad.com",
        "domain@bad.com",
        "domain%bad.com",
        "#domain.com",
        "bad-.com",
        "bad_.com",
        "-bad.com",
        "bad_.com",
        "[domain.tld",
        "domain.tld]",
        "a[aaa]a",
        "[aaa]a",
        "a[aaa]",
        "[]"
    })
    void invalidDomains(String arg) {
        assertThatThrownBy(() -> Domain.of(arg))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "domain.tld",
        "do-main.tld",
        "do_main.tld",
        "ab.dc.de.fr",
        "123.456.789.a23",
        "acv.abcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabc.fr",
        "ab--cv.fr",
        "ab__cd.fr",
        "domain",
        "[domain]",
        "127.0.0.1"
    })
    void validDomains(String arg) {
        assertThatCode(() -> Domain.of(arg))
            .doesNotThrowAnyException();
    }

    @Test
    void shouldRemoveBrackets() {
        assertThat(Domain.of("[domain]")).isEqualTo(Domain.of("domain"));
    }

    @Test
    void shouldThrowOnNullArgument() {
        assertThatThrownBy(() -> Domain.of(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldAllow253LongDomain() {
        assertThat(Domain.of(StringUtils.repeat("aaaaaaaaa.", 25) + "aaa").asString())
            .hasSize(253);
    }

    @Test
    void shouldThrowWhenTooLong() {
        assertThatThrownBy(() -> Domain.of(StringUtils.repeat('a', 254)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}