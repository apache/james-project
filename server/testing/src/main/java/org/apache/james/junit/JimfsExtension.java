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

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

/**
 * JUnit 5 extension providing a fast in-memory {@link FileSystem} backed by Google Jimfs.
 *
 * Can resolve parameters of type {@link FileSystem} or {@link Path}.
 */
public class JimfsExtension implements ParameterResolver, BeforeEachCallback, AfterEachCallback {

    private FileSystem fileSystem;
    private Path rootPath;

    @Override
    public void beforeEach(ExtensionContext context) {
        fileSystem = Jimfs.newFileSystem(Configuration.unix());
        rootPath = fileSystem.getPath("/");
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        if (parameterContext.isAnnotated(org.junit.jupiter.api.io.TempDir.class)) {
            return false;
        }
        Class<?> type = parameterContext.getParameter().getType();
        return type == FileSystem.class || type == Path.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        Class<?> type = parameterContext.getParameter().getType();
        if (type == FileSystem.class) {
            return fileSystem;
        }
        if (type == Path.class) {
            return rootPath;
        }
        throw new ParameterResolutionException("Unsupported parameter type: " + type);
    }

    @Override
    public void afterEach(ExtensionContext context) throws IOException {
        if (fileSystem != null) {
            fileSystem.close();
        }
    }

    public FileSystem getFileSystem() {
        return fileSystem;
    }

    public Path getRootPath() {
        return rootPath;
    }
}
