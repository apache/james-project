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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import jakarta.mail.MessagingException;

import org.apache.james.lifecycle.api.Disposable;
import org.apache.james.mailetcontainer.api.MailProcessor;
import org.apache.james.mailetcontainer.lib.AbstractStateCompositeProcessor;
import org.apache.james.mailetcontainer.lib.AbstractStateCompositeProcessor.CompositeProcessorListener;

/**
 * {@link CompositeProcessorListener} implementation which register MBeans for
 * its child {@link MailProcessor} and keep track of the stats
 */
public class JMXStateCompositeProcessorListener implements CompositeProcessorListener, Disposable {

    private final AbstractStateCompositeProcessor mList;
    private final MBeanServer mbeanserver;
    private final List<ObjectName> mbeans = new ArrayList<>();
    private final Map<MailProcessor, MailProcessorManagement> mMap = new HashMap<>();

    public JMXStateCompositeProcessorListener(AbstractStateCompositeProcessor mList) throws JMException {
        this.mList = mList;

        mbeanserver = ManagementFactory.getPlatformMBeanServer();
        registerMBeans();
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

    /**
     * Register all JMX MBeans
     */
    private void registerMBeans() throws JMException {

        String baseObjectName = "org.apache.james:type=component,component=mailetcontainer,name=processor,";

        String[] processorNames = mList.getProcessorStates();
        for (String processorName : processorNames) {
            registerProcessorMBean(baseObjectName, processorName);
        }
    }

    /**
     * Register a JMX MBean for a {@link MailProcessor}
     */
    private void registerProcessorMBean(String baseObjectName, String processorName) throws JMException {
        String processorMBeanName = baseObjectName + "processor=" + processorName;

        MailProcessorManagement processorDetail = new MailProcessorManagement(processorName);
        registerMBean(processorMBeanName, processorDetail);
        mMap.put(mList.getProcessor(processorName), processorDetail);

    }

    private void registerMBean(String mBeanName, Object object) throws JMException {
        ObjectName objectName = new ObjectName(mBeanName);

        mbeanserver.registerMBean(object, objectName);
        mbeans.add(objectName);

    }

    @Override
    public void afterProcessor(MailProcessor processor, String mailName, long processTime, MessagingException e) {
        MailProcessorManagement m = mMap.get(processor);
        if (m != null) {
            m.update(processTime, e == null);
        }
    }

    @Override
    public void dispose() {
        unregisterMBeans();
        mMap.clear();
    }

}
