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
package org.apache.james.container.spring.context.web;

import java.util.Objects;

import org.apache.james.container.spring.resource.DefaultJamesResourceLoader;
import org.apache.james.container.spring.resource.JamesResourceLoader;
import org.apache.james.filesystem.api.JamesDirectoriesProvider;
import org.springframework.core.io.Resource;
import org.springframework.web.context.support.XmlWebApplicationContext;

/**
 * {@link XmlWebApplicationContext} which is used to startup james in a servlet
 * container
 */
public class JamesServerWebApplicationContext extends XmlWebApplicationContext implements JamesResourceLoader {

    /**
     * The resourceloader to use
     */
    private final JamesResourceLoader resourceLoader = new DefaultJamesResourceLoader(new JamesDirectoriesProvider() {
        
        @Override
        public String getAbsoluteDirectory() {
            if (absoluteDirectory == null) {
                return getRootDirectory();
            } else {
                return absoluteDirectory;
            }
        }

        /**
         */
        @Override
        public String getConfDirectory() {
            return Objects.requireNonNullElseGet(confDirectory, () -> getRootDirectory() + "/WEB-INF/conf/");
        }

        /**
         */
        @Override
        public String getRootDirectory() {
            if (rootDirectory == null) {
                // the root dir is the same as the servlets path
                return JamesServerWebApplicationContext.this.getServletContext().getRealPath("/");
            } else {
                return rootDirectory;
            }
        }

        /**
         */
        @Override
        public String getVarDirectory() {
            return Objects.requireNonNullElseGet(varDirectory, () -> getRootDirectory() + "/var/");
        }
    });
    
    private String rootDirectory;
    private String absoluteDirectory;
    private String varDirectory;
    private String confDirectory;

    @Override
    public Resource getResource(String fileURL) {
        // delegate the loading to the resourceloader
        Resource r = resourceLoader.getResource(fileURL);
        if (r == null) {
            r = super.getResource(fileURL);
        }
        return r;
    }

    public void setRootDirectory(String rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    public void setAbsoluteDirectory(String absoluteDirectory) {
        this.absoluteDirectory = absoluteDirectory;
    }

    public void setVarDirectory(String varDirectory) {
        this.varDirectory = varDirectory;
    }

    public void setConfDirectory(String confDirectory) {
        this.confDirectory = confDirectory;
    }

    @Override
    public String getAbsoluteDirectory() {
        return resourceLoader.getAbsoluteDirectory();
    }

    @Override
    public String getConfDirectory() {
        return resourceLoader.getConfDirectory();
    }

    @Override
    public String getVarDirectory() {
        return resourceLoader.getVarDirectory();
    }

    @Override
    public String getRootDirectory() {
        return resourceLoader.getRootDirectory();
    }
}
