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

package org.apache.james.transport.mailets.utils;

import static org.assertj.guava.api.Assertions.assertThat;

import org.junit.Test;

import com.google.common.base.Optional;

public class CharsetFromSubjectMailHeaderTest {

    @Test
    public void parseShouldReturnAbsentWhenRawTextIsNull() {
        Optional<String> charset = new CharsetFromSubjectMailHeader().parse(null);

        assertThat(charset).isAbsent();
    }

    @Test
    public void parseShouldReturnAbsentWhenRawTextIsEmpty() {
        Optional<String> charset = new CharsetFromSubjectMailHeader().parse("");

        assertThat(charset).isAbsent();
    }

    @Test
    public void parseShouldReturnAbsentWhenRawTextDoesNotContainEncodingPrefix() {
        Optional<String> charset = new CharsetFromSubjectMailHeader().parse("iso-8859-2?Q?leg=FAjabb_pr=F3ba_l=F5elemmel?=");

        assertThat(charset).isAbsent();
    }

    @Test
    public void parseShouldReturnAbsentWhenRawTextDoesNotContainSecondQuestionMark() {
        Optional<String> charset = new CharsetFromSubjectMailHeader().parse("=?iso-8859-2");

        assertThat(charset).isAbsent();
    }

    @Test
    public void parseShouldReturnAbsentWhenRawTextDoesNotContainCharset() {
        Optional<String> charset = new CharsetFromSubjectMailHeader().parse("=??");

        assertThat(charset).isAbsent();
    }

    @Test
    public void parseShouldReturnAbsentWhenRawTextDoesNotContainThirdQuestionMark() {
        Optional<String> charset = new CharsetFromSubjectMailHeader().parse("=?iso-8859-2?");

        assertThat(charset).isAbsent();
    }

    @Test
    public void parseShouldReturnAbsentWhenRawTextDoesNotContainClosingTag() {
        Optional<String> charset = new CharsetFromSubjectMailHeader().parse("=?iso-8859-2?Q?leg=FAjabb_pr=F3ba_l=F5elemmel");

        assertThat(charset).isAbsent();
    }

    @Test
    public void parseShouldReturnCharsetWhenRawTextIsWellFormatted() {
        Optional<String> charset = new CharsetFromSubjectMailHeader().parse("=?iso-8859-2?Q?leg=FAjabb_pr=F3ba_l=F5elemmel?=");

        assertThat(charset).contains("iso-8859-2");
    }
}
