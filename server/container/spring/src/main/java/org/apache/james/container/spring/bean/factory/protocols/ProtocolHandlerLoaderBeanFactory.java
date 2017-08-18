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
package org.apache.james.container.spring.bean.factory.protocols;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.container.spring.bean.factory.AbstractBeanFactory;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.lib.handler.ProtocolHandlerLoader;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

public class ProtocolHandlerLoaderBeanFactory extends AbstractBeanFactory implements ProtocolHandlerLoader{

    @SuppressWarnings("unchecked")
    @Override
    public ProtocolHandler load(String name, Configuration config) throws LoadingException {
        
        try {
            // Use the classloader which is used for bean instance stuff
            Class<ProtocolHandler> c = (Class<ProtocolHandler>) getBeanFactory().getBeanClassLoader().loadClass(name);
            @SuppressWarnings("deprecation")
            ProtocolHandler handler =  (ProtocolHandler) getBeanFactory().createBean(c, AutowireCapableBeanFactory.AUTOWIRE_AUTODETECT, true);
            handler.init(config);
            return handler;
        } catch (ClassNotFoundException | BeansException | ConfigurationException e) {
            throw new LoadingException("Unable to load handler", e);
        }

    }
    
}
