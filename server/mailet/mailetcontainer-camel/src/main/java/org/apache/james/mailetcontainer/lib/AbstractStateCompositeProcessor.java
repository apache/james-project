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
package org.apache.james.mailetcontainer.lib;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.mail.MessagingException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.mailetcontainer.api.MailProcessor;
import org.apache.james.mailetcontainer.impl.jmx.JMXStateCompositeProcessorListener;
import org.apache.mailet.Mail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for {@link org.apache.james.mailetcontainer.impl.camel.CamelCompositeProcessor} which service the
 * {@link Mail} with a {@link org.apache.james.mailetcontainer.impl.camel.CamelProcessor} instances
 */
public abstract class AbstractStateCompositeProcessor implements MailProcessor, Configurable {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractStateCompositeProcessor.class);

    private final List<CompositeProcessorListener> listeners = Collections.synchronizedList(new ArrayList<CompositeProcessorListener>());
    private final Map<String, MailProcessor> processors = new HashMap<>();
    protected HierarchicalConfiguration config;

    private JMXStateCompositeProcessorListener jmxListener;
    private boolean enableJmx = true;


    public void addListener(CompositeProcessorListener listener) {
        listeners.add(listener);
    }

    public List<CompositeProcessorListener> getListeners() {
        return listeners;
    }

    public void removeListener(CompositeProcessorListener listener) {
        listeners.remove(listener);
    }

    /**
     * @see
     * org.apache.james.lifecycle.api.Configurable#configure(org.apache.commons.configuration.HierarchicalConfiguration)
     */
    public void configure(HierarchicalConfiguration config) throws ConfigurationException {
        this.config = config;
        this.enableJmx = config.getBoolean("[@enableJmx]", true);

    }

    /**
     * Service the given {@link Mail} by hand the {@link Mail} over the
     * {@link MailProcessor} which is responsible for the
     * {@link Mail#getState()}
     */
    public void service(Mail mail) throws MessagingException {
        long start = System.currentTimeMillis();
        MessagingException ex = null;
        MailProcessor processor = getProcessor(mail.getState());

        if (processor != null) {
            LOGGER.debug("Call MailProcessor {}", mail.getState());
            try {
                processor.service(mail);

                if (Mail.GHOST.equals(mail.getState())) {
                    LifecycleUtil.dispose(mail);
                }
                /*
                 * // check the mail needs further processing if
                 * (Mail.GHOST.equalsIgnoreCase(mail.getState()) == false) {
                 * service(mail); } else { LifecycleUtil.dispose(mail); }
                 */
            } catch (MessagingException e) {
                ex = e;
                throw e;
            } finally {
                long end = System.currentTimeMillis() - start;
                for (CompositeProcessorListener listener : listeners) {
                    listener.afterProcessor(processor, mail.getName(), end, ex);
                }
            }
        } else {
            throw new MessagingException("No processor found for mail " + mail.getName() + " with state " + mail.getState());
        }
    }

    /**
     * Return a {@link MailProcessor} for a given state
     * 
     * @param state
     * @return processor
     */
    public MailProcessor getProcessor(String state) {
        return processors.get(state);
    }

    public String[] getProcessorStates() {
        return processors.keySet().toArray(new String[processors.size()]);
    }

    /**
     * Check if all needed Processors are configured and if not throw a
     * {@link ConfigurationException}
     * 
     * @throws ConfigurationException
     */
    private void checkProcessors() throws ConfigurationException {
        boolean errorProcessorFound = false;
        boolean rootProcessorFound = false;
        for (String name : processors.keySet()) {
            if (name.equals(Mail.DEFAULT)) {
                rootProcessorFound = true;
            } else if (name.equals(Mail.ERROR)) {
                errorProcessorFound = true;
            }

            if (errorProcessorFound && rootProcessorFound) {
                return;
            }
        }
        if (!errorProcessorFound) {
            throw new ConfigurationException("You need to configure a Processor with name " + Mail.ERROR);
        } else if (!rootProcessorFound) {
            throw new ConfigurationException("You need to configure a Processor with name " + Mail.DEFAULT);
        }
    }

    @PostConstruct
    public void init() throws Exception {
        List<HierarchicalConfiguration> processorConfs = config.configurationsAt("processor");
        for (HierarchicalConfiguration processorConf : processorConfs) {
            String processorName = processorConf.getString("[@state]");

            // if the "child" processor has no jmx config we just use the one of
            // the composite
            if (!processorConf.containsKey("[@enableJmx]")) {
                processorConf.addProperty("[@enableJmx]", enableJmx);
            }
            processors.put(processorName, createMailProcessor(processorName, processorConf));
        }

        if (enableJmx) {
            this.jmxListener = new JMXStateCompositeProcessorListener(this);
            addListener(jmxListener);
        }

        // check if all needed processors are configured
        checkProcessors();
    }

    @PreDestroy
    public void dispose() {
        String[] names = getProcessorStates();
        for (String name : names) {
            MailProcessor processor = getProcessor(name);
            if (processor instanceof AbstractStateMailetProcessor) {
                ((AbstractStateMailetProcessor) processor).destroy();
            }

        }

        if (jmxListener != null) {
            jmxListener.dispose();
        }
    }

    /**
     * Create a new {@link MailProcessor}
     * 
     * @param state
     * @param config
     * @return container
     * @throws Exception
     */
    protected abstract MailProcessor createMailProcessor(String state, HierarchicalConfiguration config) throws Exception;

    /**
     * A Listener which will get called after
     * {@link org.apache.james.mailetcontainer.api.MailProcessor#service(Mail)} was called
     */
    public interface CompositeProcessorListener {

        /**
         * Get called after the processing via a {@link MailProcessor} was
         * complete
         * 
         * @param processor
         * @param mailName
         * @param processTime
         *            in ms
         * @param e
         *            or null if no exception was thrown
         */
        void afterProcessor(MailProcessor processor, String mailName, long processTime, MessagingException e);

    }

}
