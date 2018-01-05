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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.backends.cassandra.versions.SchemaVersion;
import org.junit.Test;

public class VersionRequestTest {
    @Test
    public void parseShouldThrowWhenNullVersion() throws Exception {
        assertThatThrownBy(() -> CassandraVersionRequest.parse(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void parseShouldThrowWhenNonIntegerVersion() throws Exception {
        assertThatThrownBy(() -> CassandraVersionRequest.parse("NoInt"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void parseShouldThrowWhenNegativeVersion() throws Exception {
        assertThatThrownBy(() -> CassandraVersionRequest.parse("-1"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void parseShouldThrowWhenZeroVersion() throws Exception {
        assertThatThrownBy(() -> CassandraVersionRequest.parse("0"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void parseShouldParseTheVersionValue() throws Exception {
        CassandraVersionRequest cassandraVersionRequest = CassandraVersionRequest.parse("1");

        assertThat(cassandraVersionRequest.getValue()).isEqualTo(new SchemaVersion(1));
    }

}