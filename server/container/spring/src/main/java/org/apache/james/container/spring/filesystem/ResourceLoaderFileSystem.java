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
package org.apache.james.container.spring.filesystem;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import org.apache.james.filesystem.api.FileSystem;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.io.ResourceLoader;

public class ResourceLoaderFileSystem implements FileSystem, ResourceLoaderAware {

    private ResourceLoader loader;

    @Override
    public InputStream getResource(String url) throws IOException {
        return loader.getResource(url).getInputStream();
    }

    @Override
    public File getFile(String fileURL) throws FileNotFoundException {
        try {
            return loader.getResource(fileURL).getFile();
        } catch (IOException e) {
            throw new FileNotFoundException("Could not load file " + fileURL);
        }
    }

    @Override
    public File getBasedir() throws FileNotFoundException {
        try {
            return loader.getResource(".").getFile();
        } catch (IOException e) {
            throw new FileNotFoundException("Could not access base directory");
        }
    }

    @Override
    public void setResourceLoader(ResourceLoader loader) {
        this.loader = loader;
    }

}
