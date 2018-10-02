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

package org.apache.james.junit;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import com.google.common.io.Files;

public class TemporaryFolderExtension implements ParameterResolver, BeforeEachCallback, AfterEachCallback {

    private TemporaryFolder temporaryFolder;

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        temporaryFolder = new TemporaryFolder(Files.createTempDir());
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return (parameterContext.getParameter().getType() == TemporaryFolder.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return temporaryFolder;
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        FileUtils.deleteDirectory(temporaryFolder.getTempDir());
    }

    public TemporaryFolder getTemporaryFolder() {
        return temporaryFolder;
    }

    public static class TemporaryFolder {
        private final File tempDir;
        private final String folderPath;

        public TemporaryFolder(File tempDir) {
            this.tempDir = tempDir;
            this.folderPath = tempDir.getPath() + "/";
        }

        public File getTempDir() {
            return tempDir;
        }

        public String getFolderPath() {
            return folderPath;
        }
    }
}
