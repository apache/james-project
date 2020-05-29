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
package org.apache.james.mailetcontainer.impl.jmx;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.james.core.MailAddress;
import org.apache.james.lifecycle.api.Disposable;
import org.apache.james.mailetcontainer.impl.matchers.CompositeMatcher;
import org.apache.james.mailetcontainer.lib.AbstractStateMailetProcessor;
import org.apache.james.mailetcontainer.lib.AbstractStateMailetProcessor.MailetProcessorListener;
import org.apache.mailet.Mailet;
import org.apache.mailet.Matcher;

/**
 * {@link MailetProcessorListener} implementation which register MBean's for all
 * the contained {@link Mailet} and {@link Matcher} and keep track of the stats
 */
public class JMXStateMailetProcessorListener implements MailetProcessorListener, Disposable {

    private final AbstractStateMailetProcessor processor;
    private final MBeanServer mbeanserver;
    private final List<ObjectName> mbeans = new ArrayList<>();
    private final Map<Mailet, MailetManagement> mailetMap = new HashMap<>();
    private final Map<Matcher, MatcherManagement> matcherMap = new HashMap<>();

    private final String name;

    public JMXStateMailetProcessorListener(String name, AbstractStateMailetProcessor processor) throws JMException {
        this.processor = processor;
        this.name = name;

        mbeanserver = ManagementFactory.getPlatformMBeanServer();
        registerMBeans();
    }

    @Override
    public void afterMailet(Mailet m, String mailName, String state, long processTime, Throwable e) {
        MailetManagement mgmt = mailetMap.get(m);
        if (mgmt != null) {
            mgmt.update(processTime, e == null);
        }
    }

    @Override
    public void afterMatcher(Matcher m, String mailName, Collection<MailAddress> rcpts, Collection<MailAddress> matches, long processTime, Throwable e) {
        MatcherManagement mgmt = matcherMap.get(m);

        if (mgmt != null) {
            boolean matched = false;
            if (matches != null) {
                matched = !matches.isEmpty();
            }
            mgmt.update(processTime, e == null, matched);
        }
    }

    /**
     * Register all JMX MBeans
     */
    private void registerMBeans() throws JMException {
        String baseObjectName = "org.apache.james:type=component,component=mailetcontainer,name=processor,processor=" + name;

        registerMailets(baseObjectName, processor.getMailets().iterator());
        registerMatchers(baseObjectName, processor.getMatchers().iterator(), 0);
    }

    /**
     * Register the Mailets as JMX MBeans
     */
    private void registerMailets(String parentMBeanName, Iterator<Mailet> mailets) throws JMException {
        int i = 0;
        while (mailets.hasNext()) {
            Mailet mailet = mailets.next();
            MailetManagement mailetManagement = new MailetManagement(mailet.getMailetConfig());

            String mailetMBeanName = parentMBeanName + ",subtype=mailet,index=" + (i++) + ",mailetname=" + mailetManagement.getMailetName();
            registerMBean(mailetMBeanName, mailetManagement);
            mailetMap.put(mailet, mailetManagement);
        }

    }

    /**
     * Register the {@link Matcher}'s as JMX MBeans
     */
    private void registerMatchers(String parentMBeanName, Iterator<Matcher> matchers, int nestingLevel) throws JMException {
        int i = 0;

        while (matchers.hasNext()) {
            Matcher matcher = matchers.next();
            MatcherManagement matcherManagement = new MatcherManagement(matcher.getMatcherConfig());

            String matcherMBeanName = parentMBeanName + ",subtype" + nestingLevel + "=matcher,index" + nestingLevel + "=" + (i++) + ",matchername" + nestingLevel + "=" + matcherManagement.getMatcherName();
            registerMBean(matcherMBeanName, matcherManagement);
            matcherMap.put(matcher, matcherManagement);
            // Handle CompositeMatcher which were added by JAMES-948
            if (matcher instanceof CompositeMatcher) {
                // we increment the nesting as we have one more child level and
                // register the child matchers
                registerMatchers(matcherMBeanName, ((CompositeMatcher) matcher).getMatchers().iterator(), nestingLevel + 1);
            }

        }
    }

    private void registerMBean(String mBeanName, Object object) throws JMException {
        ObjectName objectName = new ObjectName(mBeanName);

        mbeanserver.registerMBean(object, objectName);
        mbeans.add(objectName);

    }

    @Override
    public void dispose() {
        unregisterMBeans();
        mailetMap.clear();
        matcherMap.clear();
    }

    /**
     * Unregister all JMX MBeans
     */
    private void unregisterMBeans() {
        List<ObjectName> unregistered = new ArrayList<>();
        for (ObjectName name : mbeans) {
            try {
                mbeanserver.unregisterMBean(name);
                unregistered.add(name);
            } catch (JMException e) {
                // logger.error("Unable to unregister mbean " + name, e);
            }
        }
        mbeans.removeAll(unregistered);
    }

}
