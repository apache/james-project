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

package org.apache.james.modules.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.FileNotFoundException;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.utils.ExtendedClassLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.inject.Guice;

class PreDeletionHookLoaderImplTest {
    private PreDeletionHookLoaderImpl testee;

    @BeforeEach
    void setUp() throws Exception {
        FileSystem fileSystem = mock(FileSystem.class);
        when(fileSystem.getFile(anyString()))
            .thenThrow(new FileNotFoundException());

        testee = new PreDeletionHookLoaderImpl(Guice.createInjector(), new ExtendedClassLoader(fileSystem));
    }

    @Test
    void createHookShouldThrowWhenClassNotFound() {
        assertThatThrownBy(() -> testee.createHook(PreDeletionHookConfiguration.forClass("invalid")))
            .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void createHookShouldReturnAHookOfCreatedClass() throws Exception {
        assertThat(testee.createHook(PreDeletionHookConfiguration.forClass(NoopPreDeletionHook.class.getName())))
            .isInstanceOf(NoopPreDeletionHook.class);
    }
}