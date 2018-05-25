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

package org.apache.james.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public class SizeTest {

    @Test
    public void testNoUnit() throws Exception {
        assertThat(Size.parse("1024").asBytes()).isEqualTo(1024);
    }

    @Test
    public void testUnitB() throws Exception {
        assertThat(Size.parse("1024B").asBytes()).isEqualTo(1024);
    }

    @Test
    public void testUnitK() throws Exception {
        assertThat(Size.parse("5K").asBytes()).isEqualTo(5 * 1024);
    }

    @Test
    public void testUnitM() throws Exception {
        assertThat(Size.parse("5M").asBytes()).isEqualTo(5 * 1024 * 1024);
    }

    @Test
    public void testUnitG() throws Exception {
        assertThat(Size.parse("1G").asBytes()).isEqualTo(1024 * 1024 * 1024);
    }

    @Test
    public void testUnknown() throws Exception {
        assertThat(Size.parse("unknown").asBytes()).isEqualTo(Size.UNKNOWN_VALUE);
    }

    @Test
    public void testUnlimited() throws Exception {
        assertThat(Size.parse("unlimited").asBytes()).isEqualTo(Size.UNLIMITED_VALUE);
    }

    @Test(expected = Exception.class)
    public void testBadUnit() throws Exception {
        Size.parse("42T");
    }

    @Test(expected = NumberFormatException.class)
    public void testWrongNumber() throws Exception {
        Size.parse("42RG");
    }

}
