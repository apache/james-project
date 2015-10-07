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

import org.apache.james.container.spring.resource.AbstractJamesResourceLoader;
import org.apache.james.container.spring.resource.JamesResourceLoader;
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
    private final JamesResourceLoader resourceLoader = new AbstractJamesResourceLoader() {

        /**
         */
        public String getAbsoluteDirectory() {
            if (absoluteDirectory == null) {
                return getRootDirectory();
            } else {
                return absoluteDirectory;
            }
        }

        /**
         */
        public String getConfDirectory() {
            if (confDirectory == null) {
                return getRootDirectory() + "/WEB-INF/conf/";
            } else {
                return confDirectory;
            }
        }

        /**
         */
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
        public String getVarDirectory() {
            if (varDirectory == null) {
                return getRootDirectory() + "/var/";
            } else {
                return varDirectory;
            }
        }
    };
    private String rootDirectory;
    private String absoluteDirectory;
    private String varDirectory;
    private String confDirectory;

    /**
     * @see
     * org.springframework.core.io.DefaultResourceLoader#getResource(java.lang.String)
     */
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

    /**
     * @see
     * org.apache.james.container.spring.resource.JamesResourceLoader#getAbsoluteDirectory()
     */
    public String getAbsoluteDirectory() {
        return resourceLoader.getAbsoluteDirectory();
    }

    /**
     * @see
     * org.apache.james.container.spring.resource.JamesResourceLoader#getConfDirectory()
     */
    public String getConfDirectory() {
        return resourceLoader.getConfDirectory();
    }

    /**
     * @see
     * org.apache.james.container.spring.resource.JamesResourceLoader#getVarDirectory()
     */
    public String getVarDirectory() {
        return resourceLoader.getVarDirectory();
    }

    /**
     * @see
     * org.apache.james.container.spring.resource.JamesResourceLoader#getRootDirectory()
     */
    public String getRootDirectory() {
        return resourceLoader.getRootDirectory();
    }
}
