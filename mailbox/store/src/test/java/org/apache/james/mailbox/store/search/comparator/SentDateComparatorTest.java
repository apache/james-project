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

package org.apache.james.mailbox.store.search.comparator;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

class SentDateComparatorTest {
    @Test
    void sanitizeDateStringHeaderValueShouldRemoveCESTPart() {
        assertThat(SentDateComparator.sanitizeDateStringHeaderValue("Thu, 18 Jun 2015 04:09:35 +0200 (CEST)"))
            .isEqualTo("Thu, 18 Jun 2015 04:09:35 +0200");
    }

    @Test
    void sanitizeDateStringHeaderValueShouldRemoveUTCPart() {
        assertThat(SentDateComparator.sanitizeDateStringHeaderValue("Thu, 18 Jun 2015 04:09:35 +0200  (UTC)  "))
            .isEqualTo("Thu, 18 Jun 2015 04:09:35 +0200");
    }

    @Test
    void sanitizeDateStringHeaderValueShouldNotChangeAcceptableString() {
        assertThat(SentDateComparator.sanitizeDateStringHeaderValue("Thu, 18 Jun 2015 04:09:35 +0200"))
            .isEqualTo("Thu, 18 Jun 2015 04:09:35 +0200");
    }

    @Test
    void sanitizeDateStringHeaderValueShouldRemoveBrackets() {
        assertThat(SentDateComparator.sanitizeDateStringHeaderValue("invalid (removeMe)"))
            .isEqualTo("invalid");
    }

    @Test
    void sanitizeDateStringHeaderValueShouldKeepUnclosedBrackets() {
        assertThat(SentDateComparator.sanitizeDateStringHeaderValue("invalid (removeMe"))
            .isEqualTo("invalid (removeMe");
    }

    @Test
    void sanitizeDateStringHeaderValueShouldNotChangeEmptyString() {
        assertThat(SentDateComparator.sanitizeDateStringHeaderValue(""))
            .isEqualTo("");
    }

    @Test
    void toISODateShouldParseRFC5322InvalidHeader() {
        assertThat(SentDateComparator.toISODate("Fri,  5 Jun 2020 10:41:00 +0000 (UTC)"))
            .contains(ZonedDateTime.parse("2020-06-05T10:41Z[UTC]"));
    }
}
