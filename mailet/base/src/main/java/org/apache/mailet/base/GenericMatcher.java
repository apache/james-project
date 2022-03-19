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


package org.apache.mailet.base;

import java.util.Collection;

import jakarta.mail.MessagingException;

import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;
import org.apache.mailet.Matcher;
import org.apache.mailet.MatcherConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>GenericMatcher implements the Matcher and MatcherConfig interfaces.</p>
 * <p>GenericMatcher makes writing matchers easier. It provides simple versions of
 * the lifecycle methods init and destroy and of the methods in the MatcherConfig
 * interface. GenericMatcher also implements the log method, declared in the
 * MatcherContext interface.</p>
 * 
 * <p>To write a generic matcher, you need only override the abstract match method.</p>
 *
 * @version 1.0.0, 24/04/1999
 */
public abstract class GenericMatcher implements Matcher, MatcherConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenericMatcher.class);
    private MatcherConfig config = null;

    /**
     * Called by the mailet container to indicate to a matcher that the
     * matcher is being taken out of service.
     */
    @Override
    public void destroy() {
        //Do nothing
    }

    /**
     * <p>Returns a String containing the value of the named initialization
     * parameter, or null if the parameter does not exist.</p>
     * 
     * <p>This method is supplied for convenience. It gets the value of the
     * named parameter from the matcher's MatcherConfig object.</p>
     *
     * @return String a String containing the value of the initialization parameter
     */
    @Override
    public String getCondition() {
        return config.getCondition();
    }

    /**
     * Returns this matcher's MatcherConfig object.
     *
     * @return MatcherConfig the MatcherConfig object that initialized this matcher
     */
    @Override
    public MatcherConfig getMatcherConfig() {
        return config;
    }

    /**
     * Returns a reference to the MailetContext in which this matcher is
     * running.
     *
     * @return MailetContext the MailetContext object passed to this matcher by the init method
     */
    @Override
    public MailetContext getMailetContext() {
        return getMatcherConfig().getMailetContext();
    }

    /**
     * Returns information about the matcher, such as author, version, and
     * copyright.  By default, this method returns an empty string. Override
     * this method to have it return a meaningful value.
     *
     * @return String information about this matcher, by default an empty string
     */
    @Override
    public String getMatcherInfo() {
        return "";
    }

    /**
     * Returns the name of this matcher instance.
     *
     * @return the name of this matcher instance
     */
    @Override
    public String getMatcherName() {
        return config.getMatcherName();
    }


    /**
     * <p>Called by the matcher container to indicate to a matcher that the
     * matcher is being placed into service.</p>
     *
     * <p>This implementation stores the MatcherConfig object it receives from
     * the matcher container for alter use. When overriding this form of the
     * method, call super.init(config).</p>
     *
     * @param newConfig - the MatcherConfig object that contains
     *          configutation information for this matcher
     * @throws MessagingException
     *          if an exception occurs that interrupts the matcher's normal operation
     */
    @Override
    public void init(MatcherConfig newConfig) throws MessagingException {
        config = newConfig;
        init();
    }

    /**
     * <p>A convenience method which can be overridden so that there's no
     * need to call super.init(config).</p>
     *
     * <p>Instead of overriding init(MatcherConfig), simply override this
     * method and it will be called by GenericMatcher.init(MatcherConfig config).
     * The MatcherConfig object can still be retrieved via getMatcherConfig().</p>
     *
     * @throws MessagingException
     *          if an exception occurs that interrupts the matcher's normal operation
     */
    public void init() throws MessagingException {
        //Do nothing... can be overridden
    }

    /**
     * Writes the specified message to a matcher log file.
     *
     * @param message - a String specifying the message to be written to the log file
     * @deprecated Prefer using SLF4J LoggingFactory to get a Logger in each class
     */
    @Deprecated
    public void log(String message) {
        LOGGER.info(message);
    }

    /**
     * Writes an explanatory message and a stack trace for a given Throwable
     * exception to the matcher log file.
     *
     * @param message - a String that describes the error or exception
     * @param t - the java.lang.Throwable error or exception
     * @deprecated Prefer using SLF4J LoggingFactory to get a Logger in each class
     */
    @Deprecated
    public void log(String message, Throwable t) {
        LOGGER.error(message, t);
    }

    /**
     * <p>Called by the matcher container to allow the matcher to process a
     * message.</p>
     *
     * <p>This method is declared abstract so subclasses must override it.</p>
     *
     * @param mail - the Mail object that contains the MimeMessage and
     *          routing information
     * @return java.util.Collection - the recipients that the mailet container should have the
     *          mailet affect.
     * @throws MessagingException - if an exception occurs that interferes with the mailet's normal operation
     *          occurred
     */
    @Override
    public abstract Collection<MailAddress> match(Mail mail) throws MessagingException;
}
