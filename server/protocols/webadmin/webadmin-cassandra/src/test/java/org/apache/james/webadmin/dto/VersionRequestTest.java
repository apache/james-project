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

package org.apache.james.webadmin.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class VersionRequestTest {
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void parseShouldThrowWhenNullVersion() throws Exception {
        expectedException.expect(NullPointerException.class);

        CassandraVersionRequest.parse(null);
    }

    @Test
    public void parseShouldThrowWhenNonIntegerVersion() throws Exception {
        expectedException.expect(IllegalArgumentException.class);

        CassandraVersionRequest.parse("NoInt");
    }

    @Test
    public void parseShouldThrowWhenNegativeVersion() throws Exception {
        expectedException.expect(IllegalArgumentException.class);

        CassandraVersionRequest.parse("-1");
    }

    @Test
    public void parseShouldAcceptZeroVersion() throws Exception {
        CassandraVersionRequest cassandraVersionRequest = CassandraVersionRequest.parse("0");

        assertThat(cassandraVersionRequest.getValue()).isEqualTo(0);
    }

    @Test
    public void parseShouldParseTheVersionValue() throws Exception {
        CassandraVersionRequest cassandraVersionRequest = CassandraVersionRequest.parse("1");

        assertThat(cassandraVersionRequest.getValue()).isEqualTo(1);
    }

}