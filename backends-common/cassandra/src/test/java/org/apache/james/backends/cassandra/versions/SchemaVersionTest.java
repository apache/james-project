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
package org.apache.james.backends.cassandra.versions;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class SchemaVersionTest {

    static final SchemaVersion VERSION_1 = new SchemaVersion(1);
    static final SchemaVersion VERSION_2 = new SchemaVersion(2);
    static final SchemaVersion VERSION_3 = new SchemaVersion(3);
    static final SchemaVersion VERSION_4 = new SchemaVersion(4);

    @Test
    void listTransitionsForTargetShouldReturnEmptyOnSameVersion() {
        Assertions.assertThat(VERSION_2.listTransitionsForTarget(VERSION_2)).isEmpty();
    }

    @Test
    void listTransitionsForTargetShouldReturnEmptyOnSmallerVersion() {
        Assertions.assertThat(VERSION_2.listTransitionsForTarget(VERSION_1)).isEmpty();
    }

    @Test
    void listTransitionsForTargetShouldReturnListOfVersionsWhenGreater() {
        Assertions.assertThat(VERSION_2.listTransitionsForTarget(VERSION_4))
                .containsExactly(SchemaTransition.to(VERSION_3), SchemaTransition.to(VERSION_4));
    }

}