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

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.james.container.spring.lifecycle.ConfigurableBeanPostProcessor;
import org.apache.james.container.spring.lifecycle.ConfigurationProvider;
import org.apache.james.container.spring.lifecycle.LogEnabledBeanPostProcessor;
import org.apache.james.container.spring.lifecycle.LogProvider;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.InvalidSyntaxException;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.InitDestroyAnnotationBeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.osgi.extender.OsgiBeanFactoryPostProcessor;


@SuppressWarnings("deprecation")
public class OsgiLifecycleBeanFactoryPostProcessor implements OsgiBeanFactoryPostProcessor {


    private ConfigurationProvider confProvider;
    private LogProvider logProvider;

    public void setConfigurationProvider(ConfigurationProvider confProvider) {
        this.confProvider = confProvider;
    }

    public void setLogProvider(LogProvider logProvider) {
        this.logProvider = logProvider;
    }


    @Override
    public void postProcessBeanFactory(BundleContext context, ConfigurableListableBeanFactory factory) throws BeansException, InvalidSyntaxException, BundleException {
        // We need to set the beanfactory by hand. This MAY be a bug in spring-dm but I'm not sure yet
        LogEnabledBeanPostProcessor loggingProcessor = new LogEnabledBeanPostProcessor();
        loggingProcessor.setBeanFactory(factory);
        loggingProcessor.setLogProvider(logProvider);
        loggingProcessor.setOrder(0);
        factory.addBeanPostProcessor(loggingProcessor);
        
        OSGIResourceAnnotationBeanPostProcessor resourceProcessor = new OSGIResourceAnnotationBeanPostProcessor();
        resourceProcessor.setBeanClassLoader(factory.getBeanClassLoader());
        resourceProcessor.setBeanFactory(factory);
        resourceProcessor.setBundleContext(context);
        resourceProcessor.setTimeout(60 * 1000);
        factory.addBeanPostProcessor(resourceProcessor);
        
        OSGIPersistenceUnitAnnotationBeanPostProcessor persistenceProcessor = new OSGIPersistenceUnitAnnotationBeanPostProcessor();
        persistenceProcessor.setBeanClassLoader(factory.getBeanClassLoader());
        persistenceProcessor.setBeanFactory(factory);
        persistenceProcessor.setBundleContext(context);
        persistenceProcessor.setTimeout(60 * 1000);
        factory.addBeanPostProcessor(persistenceProcessor);
        
        ConfigurableBeanPostProcessor configurationProcessor = new ConfigurableBeanPostProcessor();
        configurationProcessor.setBeanFactory(factory);
        configurationProcessor.setConfigurationProvider(confProvider);
        configurationProcessor.setOrder(2);
        factory.addBeanPostProcessor(configurationProcessor);
        
        InitDestroyAnnotationBeanPostProcessor annotationProcessor = new InitDestroyAnnotationBeanPostProcessor();
        annotationProcessor.setInitAnnotationType(PostConstruct.class);
        annotationProcessor.setDestroyAnnotationType(PreDestroy.class);
        factory.addBeanPostProcessor(annotationProcessor);


    }

}
