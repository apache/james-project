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

package org.apache.james.repository.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class FilePersistentObjectRepositoryTest {
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private final FilePersistentObjectRepository testee = new FilePersistentObjectRepository() {
        @Override
        protected InputStream getInputStream(String key) {
            return new ByteArrayInputStream("not a serialized object".getBytes(StandardCharsets.UTF_8)) {
                @Override
                public void close() {
                    closed.set(true);
                }
            };
        }
    };

    @Test
    void getShouldCloseTheInputStreamWhenReadingFails() {
        assertThatThrownBy(() -> testee.get("key"))
            .isInstanceOf(RuntimeException.class);

        assertThat(closed).isTrue();
    }

    @Test
    void getWithClassLoaderShouldCloseTheInputStreamWhenReadingFails() {
        assertThatThrownBy(() -> testee.get("key", getClass().getClassLoader()))
            .isInstanceOf(RuntimeException.class);

        assertThat(closed).isTrue();
    }
}
