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
package org.apache.james.container.spring.bean.factorypostprocessor;

import java.io.FileNotFoundException;

import org.apache.james.filesystem.api.FileSystem;
import org.springframework.beans.BeansException;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionVisitor;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * {@link BeanFactoryPostProcessor} implementation which parse the spring
 * configuration and search for property values which are prefixed with
 * {@link #FS_PREFIX}.
 * 
 * If such a property is found it will try to resolve the given path via the
 * {@link FileSystem} service and replace it.
 */
public class FileSystemBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

    private static final String FS_PREFIX = "filesystem=";

    private final FileSystemVisitor visitor = new FileSystemVisitor();

    private FileSystem fileSystem;

    public void setFileSystem(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory factory) throws BeansException {
        String[] names = factory.getBeanDefinitionNames();
        for (String name : names) {
            BeanDefinition def = factory.getBeanDefinition(name);
            visitor.visitBeanDefinition(def);
        }
    }

    private final class FileSystemVisitor extends BeanDefinitionVisitor {
        @Override
        protected String resolveStringValue(String strVal) throws BeansException {
            if (strVal.startsWith(FS_PREFIX)) {
                try {
                    return fileSystem.getFile(strVal.substring(FS_PREFIX.length())).toString();
                } catch (FileNotFoundException e) {
                    throw new FatalBeanException("Unable to convert value with filesystem service", e);
                }
            }
            return strVal;
        }
    }

}
