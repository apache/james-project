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
package org.apache.james.container.spring.lifecycle;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;

/**
 * Load {@link HierarchicalConfiguration} for beans
 */
public interface ConfigurationProvider {

    /**
     * Register a {@link HierarchicalConfiguration} for a bean name.<br>
     * It is not mandatory to use the registerConfiguration to have the
     * configuration available as the {@link #getConfiguration(String)} method may load
     * it based on conventions (naming,...).
     * 
     * @param beanName
     *            The bean name for which the configuration has to be
     *            registered.
     * @param conf
     *            The hierarchical configuration to register for the bean name.
     */
    void registerConfiguration(String beanName, HierarchicalConfiguration<ImmutableNode> conf);

    /**
     * Load and return the configuration for the bean with the given name. The
     * configuration may already be loaded from a previous method invocation or
     * from a previous configuration registration via the
     * {@link #registerConfiguration(String, HierarchicalConfiguration)}.<br>
     * This method may implement convention based configuration loading based on
     * naming,...
     * 
     * @param beanName
     * @return config
     * @throws ConfigurationException
     */
    HierarchicalConfiguration<ImmutableNode> getConfiguration(String beanName) throws ConfigurationException;

}
