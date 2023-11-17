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

package org.apache.james.utils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.util.StreamUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;

public class ExtendedClassLoader {
    private static final Map<FullyQualifiedClassName, Optional<Class<?>>> cache = new HashMap<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtendedClassLoader.class);

    public static final String EXTENSIONS_JARS_FOLDER_NAME = "extensions-jars/";

    private final URLClassLoader urlClassLoader;

    @Inject
    public ExtendedClassLoader(FileSystem fileSystem) {
        this.urlClassLoader = new URLClassLoader(retrieveExtensionsUrls(fileSystem), getClass().getClassLoader());
    }

    private URL[] retrieveExtensionsUrls(FileSystem fileSystem) {
        try {
            File file = fileSystem.getFile("file://" + EXTENSIONS_JARS_FOLDER_NAME);
            return recursiveExpand(file)
                .toArray(URL[]::new);
        } catch (IOException e) {
            LOGGER.info("No " + EXTENSIONS_JARS_FOLDER_NAME + " folder.");
            return new URL[]{};
        }
    }

    private Stream<URL> recursiveExpand(File file) {
        return StreamUtils.ofNullable(file.listFiles())
            .flatMap(Throwing.function(this::expandFile).sneakyThrow());
    }

    private Stream<URL> expandFile(File file) throws MalformedURLException {
        if (file.isDirectory()) {
            return recursiveExpand(file);
        }
        LOGGER.info("Loading custom classpath resource {}", file.getAbsolutePath());
        return Stream.of(file.toURI().toURL());
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<Class<T>> locateClass(FullyQualifiedClassName className) {
        Optional<Class<?>> cachedValue = cache.get(className);
        if (cachedValue != null) {
            return (Optional) cachedValue;
        }
        try {
            Class<?> aClass = urlClassLoader.loadClass(className.getName());
            cache.put(className, Optional.of(aClass));
            return (Optional) Optional.of(aClass);
        } catch (ClassNotFoundException e) {
            cache.put(className, Optional.empty());
            return Optional.empty();
        }
    }
}
