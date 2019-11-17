package org.apache.james.protocols.lib.mock;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

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
