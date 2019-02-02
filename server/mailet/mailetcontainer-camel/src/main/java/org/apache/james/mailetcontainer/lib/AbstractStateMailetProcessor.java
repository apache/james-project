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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.mail.MessagingException;
import javax.management.NotCompliantMBeanException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.core.MailAddress;
import org.apache.james.lifecycle.api.Configurable;
import org.apache.james.mailetcontainer.api.MailProcessor;
import org.apache.james.mailetcontainer.api.MailetLoader;
import org.apache.james.mailetcontainer.api.MatcherLoader;
import org.apache.james.mailetcontainer.impl.MailetConfigImpl;
import org.apache.james.mailetcontainer.impl.MatcherConfigImpl;
import org.apache.james.mailetcontainer.impl.MatcherMailetPair;
import org.apache.james.mailetcontainer.impl.jmx.JMXStateMailetProcessorListener;
import org.apache.james.mailetcontainer.impl.matchers.CompositeMatcher;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.MailetContext;
import org.apache.mailet.Matcher;
import org.apache.mailet.MatcherConfig;
import org.apache.mailet.base.GenericMailet;
import org.apache.mailet.base.MatcherInverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.steveash.guavate.Guavate;

/**
 * Abstract base class for {@link MailProcessor} implementations which want to
 * process {@link Mail} via {@link Matcher} and {@link Mailet}
 */
public abstract class AbstractStateMailetProcessor implements MailProcessor, Configurable {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractStateMailetProcessor.class);

    private MailetContext mailetContext;
    private MatcherLoader matcherLoader;
    private MailProcessor rootMailProcessor;
    private final List<MailetProcessorListener> listeners = Collections.synchronizedList(new ArrayList<MailetProcessorListener>());
    private JMXStateMailetProcessorListener jmxListener;
    private boolean enableJmx = true;
    private HierarchicalConfiguration config;
    private MailetLoader mailetLoader;
    private final List<MatcherMailetPair> pairs = new ArrayList<>();
    private String state;

    public void setMatcherLoader(MatcherLoader matcherLoader) {
        this.matcherLoader = matcherLoader;
    }

    public void setRootMailProcessor(MailProcessor rootMailProcessor) {
        this.rootMailProcessor = rootMailProcessor;
    }

    @Inject
    public void setMailetContext(MailetContext mailetContext) {
        this.mailetContext = mailetContext;
    }

    @Inject
    public void setMailetLoader(MailetLoader mailetLoader) {
        this.mailetLoader = mailetLoader;
    }


    @Override
    public void configure(HierarchicalConfiguration config) throws ConfigurationException {
        this.state = config.getString("[@state]", null);
        if (state == null) {
            throw new ConfigurationException("Processor state attribute must be configured");
        }
        if (state.equals(Mail.GHOST)) {
            throw new ConfigurationException("Processor state of " + Mail.GHOST + " is reserved for internal use, choose a different one");
        }

        this.enableJmx = config.getBoolean("[@enableJmx]", true);
        this.config = config;

    }

    /**
     * Init the container
     * 
     * @throws Exception
     */
    @PostConstruct
    public void init() throws Exception {
        parseConfiguration();
        setupRouting(pairs);

        if (enableJmx) {
            this.jmxListener = new JMXStateMailetProcessorListener(state, this);
            addListener(jmxListener);
        }
    }

    /**
     * Destroy the container
     */
    @PreDestroy
    public void destroy() {
        listeners.clear();
        if (enableJmx && jmxListener != null) {
            jmxListener.dispose();
        }

        for (MatcherMailetPair pair : pairs) {
            Mailet mailet = pair.getMailet();
            Matcher matcher = pair.getMatcher();
            LOGGER.debug("Shutdown matcher {}", matcher.getMatcherInfo());
            matcher.destroy();

            LOGGER.debug("Shutdown mailet {}", mailet.getMailetInfo());
            mailet.destroy();

        }
    }

    /**
     * Hand the mail over to another processor
     * 
     * @param mail
     * @throws MessagingException
     */
    protected void toProcessor(Mail mail) throws MessagingException {
        rootMailProcessor.service(mail);
    }

    protected String getState() {
        return state;
    }

    /**
     * Return a unmodifiable {@link List} of the configured {@link Mailet}'s
     * 
     * @return mailets
     */
    public List<Mailet> getMailets() {
        return pairs.stream()
            .map(MatcherMailetPair::getMailet)
            .collect(Guavate.toImmutableList());
    }

    /**
     * Return a unmodifiable {@link List} of the configured {@link Matcher}'s
     * 
     * @return matchers
     */
    public List<Matcher> getMatchers() {
        return pairs.stream()
            .map(MatcherMailetPair::getMatcher)
            .collect(Guavate.toImmutableList());
    }

    public void addListener(MailetProcessorListener listener) {
        listeners.add(listener);
    }

    public void removeListener(MailetProcessorListener listener) {
        listeners.remove(listener);
    }

    public List<MailetProcessorListener> getListeners() {
        return listeners;
    }

    /**
     * Create a {@link MailetConfig} for the given mailetname and configuration
     * 
     * @param mailetName
     * @param configuration
     * @return mailetConfig
     */
    private MailetConfig createMailetConfig(String mailetName, HierarchicalConfiguration configuration) {

        final MailetConfigImpl configImpl = new MailetConfigImpl();
        configImpl.setMailetName(mailetName);
        configImpl.setConfiguration(configuration);
        configImpl.setMailetContext(mailetContext);
        return configImpl;
    }

    /**
     * Create a {@link MatcherConfig} for the given "match=" attribute.
     * 
     * @param matchName
     * @return matcherConfig
     */
    private MatcherConfig createMatcherConfig(String matchName) {
        String condition = null;
        int i = matchName.indexOf('=');
        if (i != -1) {
            condition = matchName.substring(i + 1);
            matchName = matchName.substring(0, i);
        }
        final MatcherConfigImpl configImpl = new MatcherConfigImpl();
        configImpl.setMatcherName(matchName);
        configImpl.setCondition(condition);
        configImpl.setMailetContext(mailetContext);
        return configImpl;

    }

    /**
     * Load {@link CompositeMatcher} implementations and their child
     * {@link Matcher}'s
     * 
     * CompositeMatcher were added by JAMES-948
     * 
     * @param compMap
     * @param compMatcherConfs
     * @return compositeMatchers
     * @throws ConfigurationException
     * @throws MessagingException
     * @throws NotCompliantMBeanException
     */
    private List<Matcher> loadCompositeMatchers(String state, Map<String, Matcher> compMap, List<HierarchicalConfiguration> compMatcherConfs) throws ConfigurationException, MessagingException {
        List<Matcher> matchers = new ArrayList<>();

        for (HierarchicalConfiguration c : compMatcherConfs) {
            String compName = c.getString("[@name]", null);
            String matcherName = c.getString("[@match]", null);
            String invertedMatcherName = c.getString("[@notmatch]", null);

            Matcher matcher = null;
            if (matcherName != null && invertedMatcherName != null) {
                // if no matcher is configured throw an Exception
                throw new ConfigurationException("Please configure only match or nomatch per mailet");
            } else if (matcherName != null) {
                matcher = matcherLoader.getMatcher(createMatcherConfig(matcherName));
                if (matcher instanceof CompositeMatcher) {
                    CompositeMatcher compMatcher = (CompositeMatcher) matcher;

                    List<Matcher> childMatcher = loadCompositeMatchers(state, compMap, c.configurationsAt("matcher"));
                    for (Matcher aChildMatcher : childMatcher) {
                        compMatcher.add(aChildMatcher);
                    }
                }
            } else if (invertedMatcherName != null) {
                Matcher m = matcherLoader.getMatcher(createMatcherConfig(invertedMatcherName));
                if (m instanceof CompositeMatcher) {
                    CompositeMatcher compMatcher = (CompositeMatcher) m;

                    List<Matcher> childMatcher = loadCompositeMatchers(state, compMap, c.configurationsAt("matcher"));
                    for (Matcher aChildMatcher : childMatcher) {
                        compMatcher.add(aChildMatcher);
                    }
                }
                matcher = new MatcherInverter(m);
            }
            if (matcher == null) {
                throw new ConfigurationException("Unable to load matcher instance");
            }
            matchers.add(matcher);
            if (compName != null) {
                // check if there is already a composite Matcher with the name
                // registered in the processor
                if (compMap.containsKey(compName)) {
                    throw new ConfigurationException("CompositeMatcher with name " + compName + " is already defined in processor " + state);
                }
                compMap.put(compName, matcher);
            }
        }
        return matchers;
    }

    private void parseConfiguration() throws MessagingException, ConfigurationException {

        // load composite matchers if there are any
        Map<String, Matcher> compositeMatchers = new HashMap<>();
        loadCompositeMatchers(getState(), compositeMatchers, config.configurationsAt("matcher"));

        final List<HierarchicalConfiguration> mailetConfs = config.configurationsAt("mailet");

        // Loop through the mailet configuration, load
        // all of the matcher and mailets, and add
        // them to the processor.
        for (HierarchicalConfiguration c : mailetConfs) {
            // We need to set this because of correctly parsing comma
            String mailetClassName = c.getString("[@class]");
            String matcherName = c.getString("[@match]", null);
            String invertedMatcherName = c.getString("[@notmatch]", null);

            Mailet mailet;
            Matcher matcher;

            try {

                if (matcherName != null && invertedMatcherName != null) {
                    // if no matcher is configured throw an Exception
                    throw new ConfigurationException("Please configure only match or nomatch per mailet");
                } else if (matcherName != null) {
                    // try to load from compositeMatchers first
                    matcher = compositeMatchers.get(matcherName);
                    if (matcher == null) {
                        // no composite Matcher found, try to load it via
                        // MatcherLoader
                        matcher = matcherLoader.getMatcher(createMatcherConfig(matcherName));
                    }
                } else if (invertedMatcherName != null) {
                    // try to load from compositeMatchers first
                    // matcherName is a known null value at this state
                    matcher = compositeMatchers.get(matcherName);
                    if (matcher == null) {
                        // no composite Matcher found, try to load it via
                        // MatcherLoader
                        matcher = matcherLoader.getMatcher(createMatcherConfig(invertedMatcherName));
                    }
                    matcher = new MatcherInverter(matcher);

                } else {
                    // default matcher is All
                    matcher = matcherLoader.getMatcher(createMatcherConfig("All"));
                }

                // The matcher itself should log that it's been inited.
                LOGGER.info("Matcher {} instantiated.", matcherName);
            } catch (MessagingException ex) {
                // **** Do better job printing out exception
                LOGGER.error("Unable to init matcher {}", matcherName, ex);
                if (ex.getNextException() != null) {
                    LOGGER.error("Caused by nested exception: ", ex.getNextException());
                }
                throw new ConfigurationException("Unable to init matcher " + matcherName, ex);
            }
            try {
                mailet = mailetLoader.getMailet(createMailetConfig(mailetClassName, c));
                LOGGER.info("Mailet {} instantiated.", mailetClassName);
            } catch (MessagingException ex) {
                // **** Do better job printing out exception
                LOGGER.error("Unable to init mailet {}", mailetClassName, ex);
                if (ex.getNextException() != null) {
                    LOGGER.error("Caused by nested exception: ", ex.getNextException());
                }
                throw new ConfigurationException("Unable to init mailet " + mailetClassName, ex);
            }

            if (matcher != null && mailet != null) {
                pairs.add(new MatcherMailetPair(matcher, mailet));
            } else {
                throw new ConfigurationException("Unable to load Mailet or Matcher");
            }
        }
    }

    /**
     * Setup the routing for the configured {@link MatcherMailetPair}'s for this
     * {@link org.apache.james.mailetcontainer.impl.camel.CamelProcessor}
     */
    protected abstract void setupRouting(List<MatcherMailetPair> pairs) throws MessagingException;

    /**
     * Mailet which protect us to not fall into an endless loop caused by an
     * configuration error
     */
    public static class TerminatingMailet extends GenericMailet {
        /**
         * The name of the mailet used to terminate the mailet chain. The end of
         * the matcher/mailet chain must be a matcher that matches all mails and
         * a mailet that sets every mail to GHOST status. This is necessary to
         * ensure that mails are removed from the spool in an orderly fashion.
         */
        private static final String TERMINATING_MAILET_NAME = "Terminating%Mailet%Name";

        @Override
        public void service(Mail mail) {
            if (!(Mail.ERROR.equals(mail.getState()))) {
                // Don't complain if we fall off the end of the
                // error processor. That is currently the
                // normal situation for James, and the message
                // will show up in the error store.
                LOGGER.warn("Message {} reached the end of this processor, and is automatically deleted. " +
                    "This may indicate a configuration error.", mail.getName());
            }

            // Set the mail to ghost state
            mail.setState(Mail.GHOST);
        }

        @Override
        public String getMailetInfo() {
            return getMailetName();
        }

        @Override
        public String getMailetName() {
            return TERMINATING_MAILET_NAME;
        }
    }

    /**
     * A Listener which will get notified after
     * {@link Mailet#service(org.apache.mailet.Mail)} and
     * {@link Matcher#match(org.apache.mailet.Mail)} methods are called from the
     * container
     */
    public interface MailetProcessorListener {

        /**
         * Get called after each {@link Mailet} call was complete
         * 
         * @param m
         * @param mailName
         * @param state
         * @param processTime
         *            in ms
         * @param e
         *            or null if no Exception was thrown
         */
        void afterMailet(Mailet m, String mailName, String state, long processTime, Exception e);

        /**
         * Get called after each {@link Matcher} call was complete
         * 
         * @param m
         * @param mailName
         * @param recipients
         * @param matches
         * @param processTime
         *            in ms
         * @param e
         *            or null if no Exception was thrown
         */
        void afterMatcher(Matcher m, String mailName, Collection<MailAddress> recipients, Collection<MailAddress> matches, long processTime, Exception e);

    }

}
