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
package org.apache.james.core.filesystem;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class ClassPathResource implements Resource {
    
    private final String path;
    private final ClassLoader classLoader;

    public ClassPathResource(String path) {
        this.path = sanitizePath(path);
        this.classLoader = getDefaultClassLoader();
    }

    private String sanitizePath(String path) {
        String pathToUse = new SimpleUrl(path).getSimplified();
        if (pathToUse.startsWith("/")) {
            return pathToUse.substring(1);
        }
        return pathToUse;
    }

    @Override
    public File getFile() throws IOException {
        URL url = getURL();
        return ResourceUtils.getFile(url, getDescription());
    }

    public URL getURL() throws IOException {
        URL url = resolveURL();
        if (url == null) {
            throw new FileNotFoundException(getDescription() + " cannot be resolved to URL because it does not exist");
        }
        return url;
    }

    protected URL resolveURL() {
        return this.classLoader.getResource(this.path);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        InputStream is = this.classLoader.getResourceAsStream(this.path);
        if (is == null) {
            throw new FileNotFoundException(getDescription() + " cannot be opened because it does not exist");
        }
        return is;
    }

    public String getDescription() {
        return "class path resource [" + path + "]";
    }

    private ClassLoader getDefaultClassLoader() {
        ClassLoader currentThreadClassLoader = getcurrentThreadClassLoader();
        if (currentThreadClassLoader != null) {
            return currentThreadClassLoader;
        }
        
        // No thread context class loader -> use class loader of this class.
        ClassLoader currentClassClassLoader = ClassPathResource.class.getClassLoader();
        if (currentClassClassLoader != null) {
            return currentClassClassLoader;
        }
        
        // getClassLoader() returning null indicates the bootstrap ClassLoader
        return getSystemClassLoader();
    }

    private ClassLoader getcurrentThreadClassLoader() {
        try {
            return Thread.currentThread().getContextClassLoader();
        } catch (Throwable ex) {
            // Cannot access thread context ClassLoader - falling back...
            return null;
        }
    }
    
    private ClassLoader getSystemClassLoader() {
        try {
            return ClassLoader.getSystemClassLoader();
        } catch (Throwable ex) {
            // Cannot access system ClassLoader - oh well, maybe the
            // caller can live with null...
            return null;
        }
    }
}