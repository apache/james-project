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

package org.apache.james.jmap.draft.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;

public class MessagePreviewGeneratorTest {
    
    private MessagePreviewGenerator testee;

    @Before
    public void setUp() {
        testee = new MessagePreviewGenerator();
    }

    @Test
    public void computeShouldReturnStringEmptyWhenEmptyTextBody() throws Exception {
        assertThat(testee.compute(Optional.empty())).isEqualTo(MessagePreviewGenerator.NO_BODY);
    }

    @Test
    public void computeShouldReturnStringEmptyWhenStringEmptyTextBody() throws Exception {
        assertThat(testee.compute(Optional.of(""))).isEqualTo(MessagePreviewGenerator.NO_BODY);
    }

    @Test
    public void computeShouldReturnStringEmptyWhenOnlySpaceTabAndBreakLines() throws Exception {
        assertThat(testee.compute(Optional.of(" \n\t "))).isEqualTo(MessagePreviewGenerator.NO_BODY);
    }

    @Test
    public void computeShouldReturnStringEmptyWhenOnlySpace() throws Exception {
        assertThat(testee.compute(Optional.of(" "))).isEqualTo(MessagePreviewGenerator.NO_BODY);
    }

    @Test
    public void computeShouldReturnStringEmptyWhenOnlyTab() throws Exception {
        assertThat(testee.compute(Optional.of("\t"))).isEqualTo(MessagePreviewGenerator.NO_BODY);
    }

    @Test
    public void computeShouldReturnStringEmptyWhenOnlyBreakLines() throws Exception {
        assertThat(testee.compute(Optional.of("\n"))).isEqualTo(MessagePreviewGenerator.NO_BODY);
    }

    @Test
    public void computeShouldReturnStringWithoutTruncation() throws Exception {
        String body = StringUtils.leftPad("a", 100, "b");

        assertThat(testee.compute(Optional.of(body)))
                .hasSize(100)
                .isEqualTo(body);
    }

    @Test
    public void computeShouldReturnStringIsLimitedTo256Length() throws Exception {
        String body = StringUtils.leftPad("a", 300, "b");
        String expected = StringUtils.leftPad("b", MessagePreviewGenerator.MAX_PREVIEW_LENGTH, "b");

        assertThat(testee.compute(Optional.of(body)))
            .hasSize(MessagePreviewGenerator.MAX_PREVIEW_LENGTH)
            .isEqualTo(expected);
    }

    @Test
    public void computeShouldReturnNormalizeSpaceString() throws Exception {
        String body = "    this      is\n      the\r           preview\t         content\n\n         ";

        assertThat(testee.compute(Optional.of(body)))
                .isEqualTo("this is the preview content");
    }
}
