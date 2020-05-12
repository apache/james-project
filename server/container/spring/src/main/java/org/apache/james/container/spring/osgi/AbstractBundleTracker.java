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
package org.apache.james.container.spring.osgi;

import java.net.URL;
import java.util.Enumeration;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.container.spring.lifecycle.ConfigurationProvider;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ApplicationContext;
import org.springframework.osgi.context.BundleContextAware;
import org.springframework.osgi.context.support.AbstractDelegatedExecutionApplicationContext;
import org.springframework.osgi.service.exporter.OsgiServicePropertiesResolver;
import org.springframework.osgi.service.exporter.support.OsgiServiceFactoryBean;

/**
 * This {@link BundleListener} use the extender pattern to scan all loaded
 * bundles if a class name with a given name is present. If so it register in
 * the {@link BeanDefinitionRegistry} and also register it to the OSG-Registry via an {@link OsgiServiceFactoryBean}
 * 
 */
public abstract class AbstractBundleTracker implements BeanFactoryAware, BundleListener, BundleContextAware, InitializingBean, DisposableBean {

    private BundleContext context;
    private String configuredClass;
    private volatile OsgiServiceFactoryBean osgiFactoryBean;
    private BeanFactory factory;
    private final Logger logger = LoggerFactory.getLogger(AbstractBundleTracker.class);
    
    @Override
    public void setBeanFactory(BeanFactory factory) throws BeansException {
        this.factory = factory;
    }

    @Override
    public void setBundleContext(BundleContext context) {
        this.context = context;
    }

    @Override
    public void bundleChanged(BundleEvent event) {
        Bundle b = event.getBundle();

        // Check if the event was fired for this class
        if (b.equals(this.context.getBundle())) {
            return;
        }

        switch (event.getType()) {
        case BundleEvent.STARTED:
            Enumeration<?> entrs = b.findEntries("/", "*.class", true);
            if (entrs != null) {

                // Loop over all the classes
                while (entrs.hasMoreElements()) {
                    URL e = (URL) entrs.nextElement();
                    String file = e.getFile();

                    String className = file.replaceAll("/", ".").replaceAll("\\.class", "").replaceFirst("\\.", "");
                    if (className.equals(configuredClass)) {
                        try {

                            BeanFactory bFactory = getBeanFactory(b.getBundleContext());
                            Class<?> clazz = getServiceClass();

                            // Create the definition and register it
                            BeanDefinitionRegistry registry = (BeanDefinitionRegistry) bFactory;
                            BeanDefinition def = BeanDefinitionBuilder.genericBeanDefinition(className).getBeanDefinition();
                            registry.registerBeanDefinition(getComponentName(), def);

                            // register the bean as service in the OSGI-Registry
                            osgiFactoryBean = new OsgiServiceFactoryBean();
                            osgiFactoryBean.setTargetBeanName(getComponentName());
                            osgiFactoryBean.setBeanFactory(bFactory);
                            osgiFactoryBean.setBundleContext(b.getBundleContext());
                            osgiFactoryBean.setInterfaces(new Class[] { clazz });
                            osgiFactoryBean.afterPropertiesSet();
                            logger.debug("Registered {} in the OSGI-Registry with interface {}", configuredClass, clazz.getName());
                        } catch (Exception e1) {
                            logger.error("Unable to register {} in the OSGI-Registry", configuredClass, e1);
                        }
                    }
                }
            }
            break;
        case BundleEvent.STOPPED:
            // check if we need to destroy the OsgiFactoryBean. This also include the unregister from the OSGI-Registry
            if (osgiFactoryBean != null) {
                osgiFactoryBean.destroy();
                osgiFactoryBean = null;
                logger.debug("Unregistered {} in the OSGI-Registry with interface {}", configuredClass, getServiceClass().getName());

            }
            break;
        default:
            break;
        }

    }

    
    /**
     * Return the {@link BeanFactory} for the given {@link BundleContext}. If none can be found we just create a new {@link AbstractDelegatedExecutionApplicationContext} and return the {@link BeanFactory} of it
     * 
     * 
     * @param bundleContext
     * @return factory
     * @throws Exception
     */
    private BeanFactory getBeanFactory(BundleContext bundleContext) throws Exception {
        final String filter = "(" + OsgiServicePropertiesResolver.BEAN_NAME_PROPERTY_KEY + "=" + bundleContext.getBundle().getSymbolicName() + ")";
        final ServiceReference<?>[] applicationContextRefs = bundleContext.getServiceReferences(ApplicationContext.class.getName(), filter);
        
        // Check if we found an ApplicationContext. If not create one
        if (applicationContextRefs == null || applicationContextRefs.length != 1) {
            
            // Create a new context which just serve as registry later
            AbstractDelegatedExecutionApplicationContext context = new AbstractDelegatedExecutionApplicationContext() {
            };
            context.setBundleContext(bundleContext);
            context.setPublishContextAsService(true);
            context.refresh();
            return context.getBeanFactory();
        } else {
            return ((ApplicationContext) bundleContext.getService(applicationContextRefs[0])).getAutowireCapableBeanFactory();
        }
       
       
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        ConfigurationProvider confProvider = factory.getBean(ConfigurationProvider.class);
        HierarchicalConfiguration<ImmutableNode> config = confProvider.getConfiguration(getComponentName());

        // Get the configuration for the class
        configuredClass = config.getString("[@class]");
        if (context != null) {
            context.addBundleListener(this);
        }
    }

    @Override
    public void destroy() throws Exception {
        // Its time to unregister the listener so we are sure resources are released
        if (context != null) {
            context.removeBundleListener(this);
        }
    }

    /**
     * Return the name of the component
     * 
     * @return name
     */
    protected abstract String getComponentName();

    /**
     * Return the class which will be used to expose the service in the OSGI
     * registry
     * 
     * @return sClass
     */
    protected abstract Class<?> getServiceClass();

}
