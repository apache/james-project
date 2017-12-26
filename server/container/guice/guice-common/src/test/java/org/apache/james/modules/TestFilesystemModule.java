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

package org.apache.james.modules;

import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.filesystem.api.JamesDirectoriesProvider;
import org.apache.james.server.core.JamesServerResourceLoader;
import org.junit.rules.TemporaryFolder;

import com.google.common.base.Throwables;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class TestFilesystemModule extends AbstractModule {
    
    private final Supplier<File> workingDirectory;

    private static File createTempDir(TemporaryFolder temporaryFolder) {
        try {
            return temporaryFolder.newFolder();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    public TestFilesystemModule(TemporaryFolder temporaryFolder) {
        this(() -> TestFilesystemModule.createTempDir(temporaryFolder));
    }

    public TestFilesystemModule(Supplier<File> workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    @Override
    protected void configure() {
        bind(JamesDirectoriesProvider.class).toInstance(new JamesServerResourceLoader(workingDirectory.get().getAbsolutePath()));
        bindConstant().annotatedWith(Names.named(CommonServicesModule.CONFIGURATION_PATH)).to(FileSystem.CLASSPATH_PROTOCOL);
    }
    
}
