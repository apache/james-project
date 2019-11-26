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

package org.apache.james.mailbox.model;

import static org.assertj.core.api.Java6Assertions.assertThat;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class MimePathTest {
    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(MimePath.class)
            .verify();
    }

    @Test
    void toStringWhenEmpty() {
        int[] empty = {};
        assertThat(new MimePath(empty).toString())
            .isEqualTo("MIMEPath:");
    }

    @Test
    void toStringWhenSingle() {
        int[] single = {1};
        assertThat(new MimePath(single).toString())
            .isEqualTo("MIMEPath:1");
    }

    @Test
    void toStringWhenMany() {
        int[] many = {1, 2, 3};
        assertThat(new MimePath(many).toString())
            .isEqualTo("MIMEPath:1.2.3");
    }
}