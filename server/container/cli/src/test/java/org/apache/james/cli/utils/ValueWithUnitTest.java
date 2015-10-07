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

package org.apache.james.cli.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mailbox.model.Quota;
import org.junit.Test;

public class ValueWithUnitTest {

    @Test
    public void testNoUnit() throws Exception {
        assertThat(ValueWithUnit.parse("1024").getConvertedValue()).isEqualTo(1024);
    }

    @Test
    public void testUnitB() throws Exception {
        assertThat(ValueWithUnit.parse("1024B").getConvertedValue()).isEqualTo(1024);
    }

    @Test
    public void testUnitK() throws Exception {
        assertThat(ValueWithUnit.parse("5K").getConvertedValue()).isEqualTo(5 * 1024);
    }

    @Test
    public void testUnitM() throws Exception {
        assertThat(ValueWithUnit.parse("5M").getConvertedValue()).isEqualTo(5 * 1024 * 1024);
    }

    @Test
    public void testUnitG() throws Exception {
        assertThat(ValueWithUnit.parse("1G").getConvertedValue()).isEqualTo(1024 * 1024 * 1024);
    }

    @Test
    public void testUnknown() throws Exception {
        assertThat(ValueWithUnit.parse("unknown").getConvertedValue()).isEqualTo(Quota.UNKNOWN);
    }

    @Test
    public void testUnlimited() throws Exception {
        assertThat(ValueWithUnit.parse("unlimited").getConvertedValue()).isEqualTo(Quota.UNLIMITED);
    }

    @Test(expected = Exception.class)
    public void testBadUnit() throws Exception {
        ValueWithUnit.parse("42T");
    }

    @Test(expected = NumberFormatException.class)
    public void testWrongNumber() throws Exception {
        ValueWithUnit.parse("42RG");
    }

}
