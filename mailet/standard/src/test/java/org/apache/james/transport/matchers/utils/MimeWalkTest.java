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

package org.apache.james.transport.matchers.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MimeWalkTest {

    @Nested
    class ConfigurationTest {
        @Test
        void shouldSupportDebugMode() {
            assertThat(MimeWalk.Configuration.parse("-d test.txt").isDebug()).isTrue();
        }

        @Test
        void debugModeShouldBeFalseByDefault() {
            assertThat(MimeWalk.Configuration.parse("test.txt").isDebug()).isFalse();
        }

        @Test
        void shouldSupportUnzipMode() {
            assertThat(MimeWalk.Configuration.parse("-z test.txt").unzipIsRequested()).isTrue();
        }

        @Test
        void unzipModeShouldBeFalseByDefault() {
            assertThat(MimeWalk.Configuration.parse("test.txt").unzipIsRequested()).isFalse();
        }
    }
}