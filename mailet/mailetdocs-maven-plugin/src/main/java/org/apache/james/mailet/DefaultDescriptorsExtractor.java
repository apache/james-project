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

package org.apache.james.mailet;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.apache.james.mailet.MailetMatcherDescriptor.Type;
import org.apache.mailet.ExcludeFromDocumentation;
import org.apache.mailet.Experimental;
import org.apache.mailet.Mailet;
import org.apache.mailet.Matcher;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import com.thoughtworks.qdox.JavaProjectBuilder;
import com.thoughtworks.qdox.model.JavaAnnotation;
import com.thoughtworks.qdox.model.JavaClass;

import sun.reflect.ReflectionFactory;

/**
 * Finds implementations of Mailet and Matchers in the source trees.
 * Extracts javadocs using QDox. <code>MailetInfo</code> is obtained by instantiation.
 */
public class DefaultDescriptorsExtractor {

    private static final String MATCHER_CLASS_NAME = Matcher.class.getName();
    private static final String MAILET_CLASS_NAME = Mailet.class.getName();
    
    private final List<MailetMatcherDescriptor> descriptors;
    
    public DefaultDescriptorsExtractor() {
        descriptors = new ArrayList<>();
    }

    /**
     * Descriptors extracted.
     * @return not null
     */
    public List<MailetMatcherDescriptor> descriptors() {
        return descriptors;
    }

    public DefaultDescriptorsExtractor extract(MavenProject project, Log log) {
        final Collection<JavaClass> classes = javaClasses(project);

        final URLClassLoader classLoader = classLoader(project, log);
        logProjectDependencies(project, log);
        logDirectories(project, log);
        
        try {
            final Class<?> mailetClass = classLoader.loadClass(MAILET_CLASS_NAME);
            final Class<?> matcherClass = classLoader.loadClass(MATCHER_CLASS_NAME);

            for (JavaClass nextClass : classes) {
                addDescriptor(log, classLoader, mailetClass, matcherClass, nextClass);
            }
        } catch (ClassNotFoundException e) {
            log.debug(e);
            log.info("No mailets in " + project.getName());
        }
        return this;
    }


    private void addDescriptor(Log log, 
            final URLClassLoader classLoader,
            final Class<?> mailetClass,
            final Class<?> matcherClass,
            final JavaClass nextClass) {
        final String nameOfNextClass = nextClass.getFullyQualifiedName();
        if (log.isDebugEnabled()) {
            log.debug("Class: " + nameOfNextClass);
        }
        
        if (isExcludedFromDocumentation(nextClass)) {
            log.debug(nameOfNextClass + " is excluded from documentation");
            return;
        }
        
        try {
            final Class<?> klass = classLoader.loadClass(nameOfNextClass);

            logConstructor(log, klass);

            final List<Class<?>> allInterfaces = getAllInterfaces(klass);
            
            if (allInterfaces.contains(mailetClass)) {
                final MailetMatcherDescriptor descriptor = describeMailet(log, nextClass,
                        nameOfNextClass, klass);
                descriptors.add(descriptor);

            } else if (allInterfaces.contains(matcherClass)) {
                final MailetMatcherDescriptor descriptor = describeMatcher(log,
                        nextClass, nameOfNextClass, klass);
                descriptors.add(descriptor);
            } else {
                logInterfaces(log, klass, allInterfaces);
            }

        } catch (NoClassDefFoundError | ClassNotFoundException e) {
            log.error("NotFound", e);
        } catch (SecurityException e) {
            log.error("Security exception", e);
        } catch (IllegalArgumentException e) {
            log.error("IllegalArgumentException", e);
        }

        logInterfacesImplemented(log, nextClass);
    }


    private void logInterfaces(Log log, Class<?> klass,
            final List<Class<?>> allInterfaces) {
        if (log.isDebugEnabled()) {
            if (allInterfaces.size() > 0) {
                log.debug("Interfaces for " + klass.getName());
                for (Class<?> allInterface : allInterfaces) {
                    log.debug("Interface: " + allInterface.getName());
                }
            } else {
                log.debug("No interfaces for " + klass.getName());
            }
        }
    }


    private MailetMatcherDescriptor describeMatcher(Log log,
            final JavaClass nextClass, String nameOfNextClass,
            final Class<?> klass) {

        MailetMatcherDescriptor result = MailetMatcherDescriptor.builder()
                .name(nextClass.getName())
                .fullyQualifiedClassName(nameOfNextClass)
                .type(Type.MATCHER)
                .info(fetchInfo(log, nameOfNextClass, klass, "getMatcherInfo", Type.MATCHER))
                .classDocs(nextClass.getComment())
                .experimental(isExperimental(nextClass));
        
        log.info("Found a Matcher: " + klass.getName());
        return result;
    }


    private Optional<String> fetchInfo(Log log, String nameOfClass, Class<?> klass, String infoMethodName, Type type) {
        try {
            Constructor<?> constructor = klass.getConstructors()[0];
            Object[ ] studentObjects = new Object[constructor.getParameterCount()];

            Object instance = constructor.newInstance(studentObjects);
            String info = (String) klass.getMethod(infoMethodName).invoke(instance);

            if (info != null && info.length() > 0) {
                return Optional.of(info);
            }
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException | SecurityException | NoSuchMethodException e) {
            e.printStackTrace();
            handleInfoLoadFailure(log, nameOfClass, type, e);
        }

        return Optional.empty();
    }


    private boolean isExperimental(JavaClass javaClass) {
        return javaClass.getAnnotations()
            .stream()
            .anyMatch((JavaAnnotation annotation) -> annotation.getType().getCanonicalName()
                    .equals(Experimental.class.getName()));
    }
    
    private boolean isExcludedFromDocumentation(JavaClass javaClass) {
        return javaClass.getAnnotations()
            .stream()
            .anyMatch((JavaAnnotation annotation) -> annotation.getType().getCanonicalName()
                    .equals(ExcludeFromDocumentation.class.getName()));
    }

    private void handleInfoLoadFailure(Log log, String nameOfClass,
            final Type type, Exception e) {
        log.info("Cannot load " + type + " info for " + nameOfClass, e);
        log.debug(e);
    }


    private MailetMatcherDescriptor describeMailet(Log log,
            final JavaClass nextClass, String nameOfNextClass,
            final Class<?> klass) {

        final MailetMatcherDescriptor result = MailetMatcherDescriptor.builder()
                .name(nextClass.getName())
                .fullyQualifiedClassName(nameOfNextClass)
                .type(Type.MAILET)
                .info(fetchInfo(log, nameOfNextClass, klass, "getMailetInfo", Type.MAILET))
                .classDocs(nextClass.getComment())
                .experimental(isExperimental(nextClass));
        
        log.info("Found a Mailet: " + klass.getName());
        return result;
    }


    private void logInterfacesImplemented(Log log, JavaClass nextClass) {
        if (log.isDebugEnabled()) {
            final List<JavaClass> implementedInterfaces = getAllInterfacesQdox(nextClass);
            for (JavaClass implemented: implementedInterfaces) {
                log.debug("Interface implemented: " + implemented);
            }
        }
    }


    private void logConstructor(Log log, Class<?> klass) {
        if (log.isDebugEnabled()) {
            try {
                log.debug("Constructor(empty): " + klass.getConstructor((Class<?>)null));
            } catch (SecurityException | NoSuchMethodException e) {
                log.debug("Cannot introspect empty constructor", e);
            }
        }
    }


    private URLClassLoader classLoader(MavenProject project, Log log) {
        URLClassLoader classLoader = null;
        try {
            @SuppressWarnings("unchecked")
            final List<String> cpes = project.getCompileClasspathElements();
            final int size = cpes.size();
            final URL[] urls = new URL[size];
            for (int k = 0; k < size; k++) {
                if (log.isDebugEnabled()) {
                    log.debug("CPE: " + cpes.get(k));
                }
                urls[k] = new File(cpes.get(k)).toURI().toURL();
            }
            classLoader = new URLClassLoader(urls);
        } catch (DependencyResolutionRequiredException e) {
            log.error("Failed to load project dependencies.", e);

        } catch (MalformedURLException e) {
            log.error("Cannot build classloader from project URLs.", e);
        }
        return classLoader;
    }


    @SuppressWarnings("unchecked")
    private Collection<JavaClass> javaClasses(MavenProject project) {
        JavaProjectBuilder builder = new JavaProjectBuilder();
        for (String s : (Iterable<String>) project.getCompileSourceRoots()) {
            builder.addSourceTree(new File(s));
        }
        return builder.getClasses();
    }


    private void logDirectories(MavenProject project, Log log) {
        if (log.isDebugEnabled()) {
            log.debug("OutDir: " + project.getBuild().getOutputDirectory());
        }
    }


    private void logProjectDependencies(MavenProject project, Log log) {
        log.debug("Logging project dependencies");
        if (log.isDebugEnabled()) {
            @SuppressWarnings("unchecked")
            final Set<Artifact> dependencies = project.getDependencyArtifacts();
            if (dependencies == null) {
                log.debug("No project dependencies");
            } else {
                for (Artifact artifact: dependencies) {
                    log.debug("DEP: " + artifact);
                }
            }
        }
    }


    private List<JavaClass> getAllInterfacesQdox(JavaClass javaClass) {
        List<JavaClass> res = new LinkedList<>();
        if (javaClass.getInterfaces() != null) {
            res.addAll(javaClass.getInterfaces());
        }
        if (javaClass.getSuperJavaClass() != null) {
            res.addAll(getAllInterfacesQdox(javaClass.getSuperJavaClass()));
        }
        return res;
    }

    private List<Class<?>> getAllInterfaces(Class<?> klass) {
        List<Class<?>> res = new LinkedList<>();
        if (klass.getInterfaces() != null) {
            Class<?>[] interfaces = klass.getInterfaces();
            for (Class<?> anInterface : interfaces) {
                res.add(anInterface);
                // add also interfaces extensions
                res.addAll(getAllInterfaces(anInterface));
            }
        }
        if (klass.getSuperclass() != null) {
            res.addAll(getAllInterfaces(klass.getSuperclass()));
        }
        return res;

    }

}
