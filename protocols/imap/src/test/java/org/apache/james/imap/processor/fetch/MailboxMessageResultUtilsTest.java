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

package org.apache.james.imap.processor.fetch;

import static org.apache.james.imap.processor.fetch.MessageResultUtils.getMatching;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.apache.james.mailbox.model.Header;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class MailboxMessageResultUtilsTest {

    private static final ImmutableList<String> NAMES = ImmutableList.of("One", "Three");

    Header headerOne;

    Header headerTwo;

    Header headerThree;

    List<Header> headers;

    @Before
    public void setUp() throws Exception {
        headerOne = new Header("One", "Value");
        headerTwo = new Header("Two", "Value");
        headerThree = new Header("Three", "Value");
        headers = new ArrayList<>();
        headers.add(headerOne);
        headers.add(headerTwo);
        headers.add(headerThree);
    }

    @Test
    public void testGetAllContent() {
        List<Header> results = MessageResultUtils.getAll(headers.iterator());
        assertThat(results.size()).isEqualTo(3);
        assertThat(results.get(0)).isEqualTo(headerOne);
        assertThat(results.get(1)).isEqualTo(headerTwo);
        assertThat(results.get(2)).isEqualTo(headerThree);
    }

    @Test
    public void testGetMatching() {
        List<Header> results = MessageResultUtils
                .getMatching(NAMES, headers.iterator());
        assertThat(results.size()).isEqualTo(2);
        assertThat(results.get(0)).isEqualTo(headerOne);
        assertThat(results.get(1)).isEqualTo(headerThree);
    }

    @Test
    public void testGetNotMatching() {
        List<Header> results = MessageResultUtils.getNotMatching(NAMES, headers
                .iterator());
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0)).isEqualTo(headerTwo);
    }

    @Test
    public void testGetMatchingSingle() {
        assertThat(MessageResultUtils.getMatching("One", headers
                .iterator())).isEqualTo(headerOne);
        assertThat(MessageResultUtils.getMatching("Three",
                headers.iterator())).isEqualTo(headerThree);
        assertThat(getMatching("Missing", headers.iterator())).isNull();
    }

}
