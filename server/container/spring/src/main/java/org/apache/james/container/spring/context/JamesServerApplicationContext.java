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
package org.apache.james.container.spring.context;

import org.apache.james.container.spring.resource.AbstractJamesResourceLoader;
import org.apache.james.container.spring.resource.JamesResourceLoader;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.io.Resource;

/**
 * {@link ApplicationContext} which loads all needed Spring beans for JAMES
 */
public class JamesServerApplicationContext extends ClassPathXmlApplicationContext implements JamesResourceLoader {

    /**
     * The resourceloader to use. This must be defined as static, otherwise it
     * will fail to startup..
     */
    private final static JamesServerResourceLoader resourceLoader = new JamesServerResourceLoader();

    public JamesServerApplicationContext(String[] configs) {
        super(configs);
    }

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

    /**
     * Protected accessor for the resource loader.
     */
    protected static JamesServerResourceLoader getResourceLoader() {
        return resourceLoader;
    }

    protected static final class JamesServerResourceLoader extends AbstractJamesResourceLoader {

        /**
         * @see org.apache.james.container.spring.resource.JamesResourceLoader#getAbsoluteDirectory()
         */
        public String getAbsoluteDirectory() {
            return "/";
        }

        /**
         * @see
         * org.apache.james.container.spring.resource.JamesResourceLoader#getConfDirectory()
         */
        public String getConfDirectory() {
            return getRootDirectory() + "/conf/";
        }

        /**
         * @see
         * org.apache.james.container.spring.resource.JamesResourceLoader#getVarDirectory()
         */
        public String getVarDirectory() {
            return getRootDirectory() + "/var/";
        }

        /**
         * Return the directory where the external jar libraries must be placed
         * by the administrator. The jars may contain mailets, jdbc drivers,...
         * 
         * @return externalLibraryDirectory
         */
        public String getExternalLibraryDirectory() {
            return getRootDirectory() + "/conf/lib/";
        }

        /**
         * @see
         * org.apache.james.container.spring.resource.JamesResourceLoader#getRootDirectory()
         */
        public String getRootDirectory() {
            return "../";
        }

    }

}
