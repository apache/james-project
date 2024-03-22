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
package org.apache.james.protocols.lib.mock;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.lib.handler.ProtocolHandlerLoader;

import com.google.common.collect.ImmutableList;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

public class MockProtocolHandlerLoader implements ProtocolHandlerLoader {

    public static class Builder {
        private final ImmutableList.Builder<Module> modules;

        private Builder() {
            modules = ImmutableList.builder();
        }

        public <T, U extends T> Builder put(Module module) {
            modules.add(module);
            return this;
        }

        public MockProtocolHandlerLoader build() {
            return new MockProtocolHandlerLoader(Guice.createInjector(modules.build()));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private final Injector injector;
    private final List<Object> loaderRegistry = new ArrayList<>();

    private MockProtocolHandlerLoader(Injector injector) {
        this.injector = injector;
    }

    @Override
    public ProtocolHandler load(String name, Configuration config) throws LoadingException {
        try {
            Class<?> aClass = Thread.currentThread().getContextClassLoader().loadClass(name);
            ProtocolHandler obj = (ProtocolHandler) injector.getInstance(aClass);
            postConstruct(obj);
            init(obj, config);
            synchronized (this) {
                loaderRegistry.add(obj);
            }
            return obj;
        } catch (Exception e) {
            throw new LoadingException("Unable to load protocolhandler", e);
        }
    }

    /**
     * Dispose all loaded instances by calling the method of the instances which
     * is annotated with @PreDestroy
     */
    public synchronized void dispose() {
        for (Object aLoaderRegistry : loaderRegistry) {
            try {
                preDestroy(aLoaderRegistry);
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        loaderRegistry.clear();
    }

    private void postConstruct(Object resource) throws IllegalAccessException, InvocationTargetException {
        Method[] methods = resource.getClass().getMethods();
        for (Method method : methods) {
            PostConstruct postConstructAnnotation = method.getAnnotation(PostConstruct.class);
            if (postConstructAnnotation != null) {
                Object[] args = {};
                method.invoke(resource, args);

            }
        }
    }

    private void preDestroy(Object resource) throws IllegalAccessException, InvocationTargetException {
        Method[] methods = resource.getClass().getMethods();
        for (Method method : methods) {
            PreDestroy preDestroyAnnotation = method.getAnnotation(PreDestroy.class);
            if (preDestroyAnnotation != null) {
                Object[] args = {};
                method.invoke(resource, args);

            }
        }
    }

    private void init(Object resource, Configuration config) throws IllegalAccessException, InvocationTargetException {
        Method[] methods = resource.getClass().getMethods();
        for (Method method : methods) {
            if (isInit(method)) {
                Object[] args = { config };
                method.invoke(resource, args);
            }
        }
    }
    
    private boolean isInit(Method method) {
        return method.getName().equals("init")
            && method.getParameterTypes().length == 1
            && method.getParameterTypes()[0].equals(Configuration.class);
    }
}
