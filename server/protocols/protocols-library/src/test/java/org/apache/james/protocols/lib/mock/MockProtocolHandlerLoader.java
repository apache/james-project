package org.apache.james.protocols.lib.mock;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.configuration.Configuration;
import org.apache.james.protocols.api.handler.ProtocolHandler;
import org.apache.james.protocols.lib.handler.ProtocolHandlerLoader;

public class MockProtocolHandlerLoader implements ProtocolHandlerLoader{

    @Override
    public ProtocolHandler load(String name, Configuration config) throws LoadingException {
        try {
            ProtocolHandler obj = create(name);
            injectResources(obj);
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

    private final Map<String, Object> servicesByName = new HashMap<String, Object>();
    public Object get(String name) {
        Object service = servicesByName.get(name);
        return service;
    }

    public void put(String role, Object service) {
        servicesByName.put(role, service);
    }

    private final List<Object> loaderRegistry = new ArrayList<Object>();

    protected ProtocolHandler create(String className) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        return (ProtocolHandler) Thread.currentThread().getContextClassLoader().loadClass(className).newInstance();
    }

    /**
     * Dispose all loaded instances by calling the method of the instances which
     * is annotated with @PreDestroy
     */
    public synchronized void dispose() {
        for (Object aLoaderRegistry : loaderRegistry) {
            try {
                preDestroy(aLoaderRegistry);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
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

    private void injectResources(Object resource) {
        final Method[] methods = resource.getClass().getMethods();
        for (Method method : methods) {
            final Inject injectAnnotation = method.getAnnotation(Inject.class);
            if (injectAnnotation != null) {
                String name = null;
                Annotation[][] paramAnnotations = method.getParameterAnnotations();
                if (paramAnnotations.length == 1) {
                    if (paramAnnotations[0].length ==1) {
                        if (paramAnnotations[0][0].annotationType().equals(Named.class)) {
                            name = ((Named) paramAnnotations[0][0]).value();
                        }
                    }
                }
                if (name == null) {
                    throw new UnsupportedOperationException("@Inject annotation without @Named specified is not supported by this implementation");
                } else {
                    // Name indicates a service
                    final Object service = getObjectForName(name);
                    if (service == null) {
                        throw new RuntimeException("Injection failed for object " + resource + " on method " + method + " with resource name " + name + ", because no mapping was found");
                    } else {
                        try {
                            Object[] args = { service };
                            method.invoke(resource, args);
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException("Injection failed for object " + resource + " on method " + method + " with resource " + service, e);
                        } catch (IllegalArgumentException e) {
                            throw new RuntimeException("Injection failed for object " + resource + " on method " + method + " with resource " + service, e);
                        } catch (InvocationTargetException e) {
                            throw new RuntimeException("Injection failed for object " + resource + " on method " + method + " with resource " + service, e);
                        }
                    }
                }
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
    
    public Object getObjectForName(String name) {
        return get(name);
    }

}
