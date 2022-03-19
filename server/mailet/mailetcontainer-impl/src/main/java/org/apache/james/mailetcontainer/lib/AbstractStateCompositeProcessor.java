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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import jakarta.mail.MessagingException;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.mailetcontainer.api.MailProcessor;
import org.apache.james.mailetcontainer.impl.CompositeProcessorImpl;
import org.apache.james.mailetcontainer.impl.MailetProcessorImpl;
import org.apache.james.mailetcontainer.impl.MatcherMailetPair;
import org.apache.james.mailetcontainer.impl.ProcessorImpl;
import org.apache.james.mailetcontainer.impl.jmx.JMXStateCompositeProcessorListener;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.ProcessingState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

/**
 * Abstract base class for {@link CompositeProcessorImpl} which service the
 * {@link Mail} with a {@link ProcessorImpl} instances
 */
public abstract class AbstractStateCompositeProcessor implements MailProcessor, Configurable {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractStateCompositeProcessor.class);

    private final Collection<CompositeProcessorListener> listeners = new ConcurrentLinkedDeque<>();
    private final Map<String, MailProcessor> processors = new HashMap<>();
    protected HierarchicalConfiguration<ImmutableNode> config;

    private JMXStateCompositeProcessorListener jmxListener;
    private boolean enableJmx = true;


    public void addListener(CompositeProcessorListener listener) {
        listeners.add(listener);
    }

    @Override
    public void configure(HierarchicalConfiguration<ImmutableNode> config) {
        this.config = config;
        this.enableJmx = config.getBoolean("[@enableJmx]", true);

    }

    @Override
    public void service(Mail mail) throws MessagingException {
        handleWithProcessor(mail, getProcessorOrFallBackToError(mail));
    }

    private MailProcessor getProcessorOrFallBackToError(Mail mail) {
        return Optional.ofNullable(getProcessor(mail.getState()))
            .orElseGet(() -> {
                mail.setErrorMessage("MailProcessor '" + mail.getState() + "' could not be found. Processing to 'error' instead");
                LOGGER.error("MailProcessor '{}' could not be found. Processing {} to 'error' instead", mail.getState(), mail.getName());
                mail.setState(Mail.ERROR);
                return getProcessor(Mail.ERROR);
            });
    }

    private void handleWithProcessor(Mail mail, MailProcessor processor) throws MessagingException {
        MessagingException ex = null;
        long start = System.currentTimeMillis();
        LOGGER.debug("Call MailProcessor {}", mail.getState());
        try {
            processor.service(mail);

            if (Mail.GHOST.equals(mail.getState())) {
                LifecycleUtil.dispose(mail);
            }
        } catch (MessagingException e) {
            ex = e;
            throw e;
        } finally {
            long end = System.currentTimeMillis() - start;
            for (CompositeProcessorListener listener : listeners) {
                listener.afterProcessor(processor, mail.getName(), end, ex);
            }
        }
    }

    /**
     * Return a {@link MailProcessor} for a given state
     */
    public MailProcessor getProcessor(String state) {
        return processors.get(state);
    }

    public String[] getProcessorStates() {
        return processors.keySet().toArray(String[]::new);
    }

    /**
     * Check if all needed Processors are configured and if not throw a
     * {@link ConfigurationException}
     */
    private void checkProcessors() throws ConfigurationException {
        if (!processors.containsKey(Mail.ERROR)) {
            throw new ConfigurationException("You need to configure a Processor with name " + Mail.ERROR);
        }
        if (!processors.containsKey(Mail.DEFAULT)) {
            throw new ConfigurationException("You need to configure a Processor with name " + Mail.DEFAULT);
        }
        ImmutableList<ProcessingState> missingProcessors = processors.values()
            .stream()
            .filter(MailetProcessorImpl.class::isInstance)
            .map(MailetProcessorImpl.class::cast)
            .flatMap(processor -> processor.getPairs().stream().map(MatcherMailetPair::getMailet))
            .flatMap(this::requiredProcessorStates)
            .filter(state -> !state.equals(new ProcessingState("propagate")) && !state.equals(new ProcessingState("ignore")))
            .filter(state -> !processors.containsKey(state.getValue()))
            .collect(ImmutableList.toImmutableList());

        if (!missingProcessors.isEmpty()) {
            throw new ConfigurationException("Your configurations specifies the following undefined processors: " + missingProcessors);
        }
    }

    private Stream<ProcessingState> requiredProcessorStates(Mailet mailet) {
        return Stream.concat(mailet.requiredProcessingState().stream(),
            Stream.of(
                    Optional.ofNullable(mailet.getMailetConfig().getInitParameter("onMailetException")),
                    Optional.ofNullable(mailet.getMailetConfig().getInitParameter("onMatcherException")))
                .flatMap(Optional::stream)
                .map(ProcessingState::new));
    }

    @PostConstruct
    public void init() throws Exception {
        List<HierarchicalConfiguration<ImmutableNode>> processorConfs = config.configurationsAt("processor");
        for (HierarchicalConfiguration<ImmutableNode> processorConf : processorConfs) {
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
     */
    protected abstract MailProcessor createMailProcessor(String state, HierarchicalConfiguration<ImmutableNode> config) throws Exception;

    /**
     * A Listener which will get called after
     * {@link org.apache.james.mailetcontainer.api.MailProcessor#service(Mail)} was called
     */
    public interface CompositeProcessorListener {

        /**
         * Get called after the processing via a {@link MailProcessor} was
         * complete
         * @param processTime
         *            in ms
         * @param e
         *            or null if no exception was thrown
         */
        void afterProcessor(MailProcessor processor, String mailName, long processTime, MessagingException e);

    }

}
