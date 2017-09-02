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

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.osgi.context.BundleContextAware;
import org.springframework.osgi.service.importer.support.Cardinality;
import org.springframework.osgi.service.importer.support.OsgiServiceCollectionProxyFactoryBean;
import org.springframework.osgi.service.importer.support.OsgiServiceProxyFactoryBean;
import org.springframework.util.ReflectionUtils;

/**
 * Abstract base class for {@link BeanPostProcessor} implementations which need to wire stuff via annotations and need to be functional via OSGI.
 * 
 * Many of this code is borrowed from the spring-dm's class <code>org.springframework.osgi.extensions.annotation.ServiceReferenceInjectionBeanPostProcessor.</code>
 *  * 
 * 
 *
 * @param <A>
 */
public abstract class AbstractOSGIAnnotationBeanPostProcessor<A extends Annotation> extends InstantiationAwareBeanPostProcessorAdapter implements BundleContextAware, BeanClassLoaderAware, BeanFactoryAware{

    public final static long DEFAULT_TIMEOUT = 60 * 1000* 5;
    private BundleContext bundleContext;

    private static final Logger logger = LoggerFactory.getLogger(AbstractOSGIAnnotationBeanPostProcessor.class);

    protected BeanFactory beanFactory;

    private ClassLoader classLoader;

    private boolean lookupBeanFactory = true;

    private long timeout = DEFAULT_TIMEOUT;


    /**
     * Set the timeout in milliseconds. The default is 5 minutes
     * 
     * @param timeout
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }
    public void setLookupBeanFactory(boolean lookupBeanFactory) {
        this.lookupBeanFactory = lookupBeanFactory;
    }
    
    private abstract static class ImporterCallAdapter {

        @SuppressWarnings("rawtypes")
        static void setInterfaces(Object importer, Class[] classes) {
            if (importer instanceof OsgiServiceProxyFactoryBean)
                ((OsgiServiceProxyFactoryBean) importer).setInterfaces(classes);
            else
                ((OsgiServiceCollectionProxyFactoryBean) importer).setInterfaces(classes);
        }

        static void setBundleContext(Object importer, BundleContext context) {
            ((BundleContextAware) importer).setBundleContext(context);
        }

        static void setBeanClassLoader(Object importer, ClassLoader cl) {
            ((BeanClassLoaderAware) importer).setBeanClassLoader(cl);
        }

        static void setCardinality(Object importer, Cardinality cardinality) {
            if (importer instanceof OsgiServiceProxyFactoryBean)
                ((OsgiServiceProxyFactoryBean) importer).setCardinality(cardinality);
            else
                ((OsgiServiceCollectionProxyFactoryBean) importer).setCardinality(cardinality);
        }


        static void afterPropertiesSet(Object importer) throws Exception {
            ((InitializingBean) importer).afterPropertiesSet();
        }

        static void setFilter(Object importer, String filter) {
            if (importer instanceof OsgiServiceProxyFactoryBean)
                ((OsgiServiceProxyFactoryBean) importer).setFilter(filter);
            else
                ((OsgiServiceCollectionProxyFactoryBean) importer).setFilter(filter);
        }


        @SuppressWarnings("unused")
        static void setServiceBean(Object importer, String name) {
            if (importer instanceof OsgiServiceProxyFactoryBean)
                ((OsgiServiceProxyFactoryBean) importer).setServiceBeanName(name);
            else
                ((OsgiServiceCollectionProxyFactoryBean) importer).setServiceBeanName(name);
        }
    }

    /**
     * @see org.springframework.beans.factory.BeanClassLoaderAware#setBeanClassLoader(java.lang.ClassLoader)
     */
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * process FactoryBean created objects, since these will not have had
     * services injected.
     * 
     * @param bean
     * @param beanName
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (logger.isDebugEnabled())
            logger.debug("processing [" + bean.getClass().getName() + ", " + beanName + "]");
        // Catch FactoryBean created instances.
        if (!(bean instanceof FactoryBean) && beanFactory.containsBean(BeanFactory.FACTORY_BEAN_PREFIX + beanName)) {
            injectServices(bean, beanName);
        }
        return bean;
    }

    /* private version of the injector can use */
    private void injectServices(final Object bean, final String beanName) {
        ReflectionUtils.doWithMethods(bean.getClass(),
            method -> {
                A s = AnnotationUtils.getAnnotation(method, getAnnotation());
                if (s != null && method.getParameterTypes().length == 1) {
                    try {
                        if (logger.isDebugEnabled())
                            logger.debug("Processing annotation [" + s + "] for [" + bean.getClass().getName() + "."
                                + method.getName() + "()] on bean [" + beanName + "]");
                        method.invoke(bean, getServiceImporter(s, method, beanName).getObject());
                    }
                    catch (Exception e) {
                        throw new IllegalArgumentException("Error processing annotation " +s , e);
                    }
                }
            });
    }

    @Override
    @SuppressWarnings("rawtypes")
    public PropertyValues postProcessPropertyValues(PropertyValues pvs, PropertyDescriptor[] pds, Object bean,
            String beanName) throws BeansException {

        MutablePropertyValues newprops = new MutablePropertyValues(pvs);
        for (PropertyDescriptor pd : pds) {
            A s = hasAnnotatedProperty(pd);
            if (s != null && !pvs.contains(pd.getName())) {
                try {
                    if (logger.isDebugEnabled())
                        logger.debug("Processing annotation [" + s + "] for [" + beanName + "." + pd.getName() + "]");
                    FactoryBean importer = getServiceImporter(s, pd.getWriteMethod(), beanName);
                    // BPPs are created in stageOne(), even though they are run in stageTwo(). This check means that
                    // the call to getObject() will not fail with ServiceUnavailable. This is safe to do because
                    // ServiceReferenceDependencyBeanFactoryPostProcessor will ensure that mandatory services are
                    // satisfied before stageTwo() is run.
                    if (bean instanceof BeanPostProcessor) {
                        ImporterCallAdapter.setCardinality(importer, Cardinality.C_0__1);
                    }
                    newprops.addPropertyValue(pd.getName(), importer.getObject());
                }
                catch (Exception e) {
                    throw new FatalBeanException("Could not create service reference", e);
                }
            }
        }
        return newprops;
    }

    @SuppressWarnings("rawtypes")
    private FactoryBean getServiceImporter(A s, Method writeMethod, String beanName) throws Exception {
        // Invocations will block here, so although the ApplicationContext is
        // created nothing will
        // proceed until all the dependencies are satisfied.
        Class<?>[] params = writeMethod.getParameterTypes();
        if (params.length != 1) {
            throw new IllegalArgumentException("Setter for [" + beanName + "] must have only one argument");
        }

        if (lookupBeanFactory) {
            if (logger.isDebugEnabled())
                logger.debug("Lookup the bean via the BeanFactory");
            
            final Class<?> clazz = writeMethod.getParameterTypes()[0];
            Object bean;
            try {
                bean = getBeanFromFactory(s, clazz);
            } catch (NoSuchBeanDefinitionException e) {
                // We was not able to find the bean in the factory so fallback to the osgi registry
                bean = null;
            }
            
            if (bean != null) {
                final Object fBean = bean;
                
                // Create a new FactoryBean which just return the found beab
                return new FactoryBean() {

                    @Override
                    public Object getObject() throws Exception {
                        return fBean;
                    }

                    @Override
                    public Class getObjectType() {
                        return fBean.getClass();
                    }

                    @Override
                    public boolean isSingleton() {
                        return true;
                    }
                };
            }
        }
        // The bean was not found in the BeanFactory. Its time to lookup it via the OSGI-Registry
        return getResourceProperty(new OsgiServiceProxyFactoryBean(), getFilter(s), writeMethod, beanName);
    }

    

    @SuppressWarnings("rawtypes")
    private FactoryBean getResourceProperty(OsgiServiceProxyFactoryBean pfb,  String filter, Method writeMethod, String beanName) throws Exception {
        pfb.setTimeout(timeout);
        
        // check if the we have a name for the requested bean. If so we set the filter for it
        if (filter != null) {
            ImporterCallAdapter.setFilter(pfb, filter );
        }
        ImporterCallAdapter.setInterfaces(pfb, writeMethod.getParameterTypes());
        
        ImporterCallAdapter.setBundleContext(pfb, bundleContext);
        ImporterCallAdapter.setBeanClassLoader(pfb, classLoader);
        ImporterCallAdapter.afterPropertiesSet(pfb);
        return pfb;
    }


    private A hasAnnotatedProperty(PropertyDescriptor propertyDescriptor) {
        Method setter = propertyDescriptor.getWriteMethod();
        return setter != null ? AnnotationUtils.getAnnotation(setter, getAnnotation()) : null;
    }

    /**
     * @see org.springframework.osgi.context.BundleContextAware#setBundleContext(org.osgi.framework.BundleContext)
     */
    public void setBundleContext(BundleContext context) {
        this.bundleContext = context;
    }

    /**
     * @see org.springframework.beans.factory.BeanFactoryAware#setBeanFactory(org.springframework.beans.factory.BeanFactory)
     */
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }
    

    /**
     * Return the class of the {@link Annotation}
     * 
     * @return clazz
     */
    protected abstract Class<A> getAnnotation();
    
    /**
     * Return the filter which should get used to lookup the service in the osgi registry.
     * If no special filter should be used, just return null
     * 
     * @param annotation
     * @return filter
     */
    protected abstract String getFilter(A annotation);
    
    /**
     * Return the Bean lookup-ed from the {@link BeanFactory}. If non can be found just return null
     * 
     * @param a
     * @param clazz
     * @return bean
     */
    protected abstract Object getBeanFromFactory(A a, Class<?> clazz);
    
}

