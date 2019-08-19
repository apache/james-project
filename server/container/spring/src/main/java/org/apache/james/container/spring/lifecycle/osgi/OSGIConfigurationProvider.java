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
package org.apache.james.container.spring.lifecycle.osgi;

import java.io.FileInputStream;
import java.io.IOException;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.server.core.configuration.FileConfigurationProvider;

public class OSGIConfigurationProvider implements org.apache.james.container.spring.lifecycle.ConfigurationProvider {

    @Override
    public void registerConfiguration(String beanName, HierarchicalConfiguration conf) {
        
    }

    @Override
    public HierarchicalConfiguration getConfiguration(String beanName) throws ConfigurationException {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream("/tmp/" + beanName + ".xml");
            return FileConfigurationProvider.getConfig(fis);
        } catch (IOException e) {
            throw new ConfigurationException("Bean " + beanName);
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (Exception e) {
                    // Left empty on purpose
                }
            }
        }
    }

}
