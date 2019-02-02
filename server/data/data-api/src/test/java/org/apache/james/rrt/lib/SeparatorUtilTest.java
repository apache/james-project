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

import org.junit.jupiter.api.Test;

public class SeparatorUtilTest {

    @Test
    public void getSeparatorShouldReturnCommaWhenCommaIsPresent() {
        String separator = SeparatorUtil.getSeparator("regex:(.*)@localhost, regex:user@test");
        assertThat(separator).isEqualTo(",");
    }

    @Test
    public void getSeparatorShouldReturnEmptyWhenColonIsPresentInPrefix() {
        String separator = SeparatorUtil.getSeparator("regex:(.*)@localhost");
        assertThat(separator).isEqualTo("");
    }

    @Test
    public void getSeparatorShouldReturnEmptyWhenColonIsPresent() {
        String separator = SeparatorUtil.getSeparator("(.*)@localhost: user@test");
        assertThat(separator).isEqualTo(":");
    }

    @Test
    public void getSeparatorShouldReturnColonWhenNoSeparator() {
        String separator = SeparatorUtil.getSeparator("user@test");
        assertThat(separator).isEqualTo(":");
    }
}