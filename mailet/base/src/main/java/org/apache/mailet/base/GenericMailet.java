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

import static java.util.function.Predicate.not;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.mail.MessagingException;

import org.apache.commons.lang3.StringUtils;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.MailetContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;

/**
 * GenericMailet makes writing mailets easier. It provides simple
 * versions of the lifecycle methods init and destroy and of the methods
 * in the MailetConfig interface. GenericMailet also implements the log
 * method, declared in the MailetContext interface.
 * <p>
 * To write a generic mailet, you need only override the abstract service
 * method.
 *
 * @version 1.0.0, 24/04/1999
 */
public abstract class GenericMailet implements Mailet, MailetConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenericMailet.class);

    private static final String YES = "yes";
    private static final String NO = "no";
    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private static final String CONFIG_IS_NULL_ERROR_MESSAGE = "Mailet configuration must be set before getInitParameter is called.";
    private static final List<String> ERROR_PARAMETERS = ImmutableList.of("onMailetException", "onMatchException");

    private MailetConfig config = null;

    /**
     * Called by the mailer container to indicate to a mailet that the
     * mailet is being taken out of service.
     */
    @Override
    public void destroy() {
        //Do nothing
    }

    /**
     * <p>Gets a boolean valued init parameter.</p>
     * <p>A convenience method. The result is parsed
     * from the value of the named parameter in the {@link MailetConfig}.</p>
     *
     * @param name name of the init parameter to be queried
     * @param defaultValue this value will be substituted when the named value
     * cannot be parse or when the init parameter is absent
     * @return true when the init parameter is <code>true</code> (ignoring case);
     * false when the init parameter is <code>false</code> (ignoring case);
     * otherwise the default value
     * @throws NullPointerException before {@link #init(MailetConfig)}
     */
    public boolean getInitParameter(String name, boolean defaultValue) {
        Preconditions.checkState(config != null, CONFIG_IS_NULL_ERROR_MESSAGE);
        return MailetUtil.getInitParameter(config, name).orElse(defaultValue);
    }

    public Optional<String> getInitParameterAsOptional(String name) {
        String value = getInitParameter(name);
        if (Strings.isNullOrEmpty(value)) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    /**
     * Gets a boolean valued init parameter that matches 'false', 'no', 'true' or 'yes' string values.
     */
    public boolean getBooleanParameter(String value, boolean defaultValue) {
        if (defaultValue) {
            return !isFalseOrNo(value);
        }
        return isTrueOrYes(value);
    }

    private static boolean isFalseOrNo(String value) {
        return StringUtils.containsIgnoreCase(value, FALSE) || StringUtils.containsIgnoreCase(value, NO);
    }

    private static boolean isTrueOrYes(String value) {
        return StringUtils.containsIgnoreCase(value, TRUE) || StringUtils.containsIgnoreCase(value, YES);
    }

    /**
     * Returns a String containing the value of the named initialization
     * parameter, or null if the parameter does not exist.
     * <p>
     * This method is supplied for convenience. It gets the value of the
     * named parameter from the mailet's MailetConfig object.
     *
     * @param name - a String specifying the name of the initialization parameter
     * @return a String containing the value of the initialization parameter
     */
    @Override
    public String getInitParameter(String name) {
        Preconditions.checkState(config != null, CONFIG_IS_NULL_ERROR_MESSAGE);
        return config.getInitParameter(name);
    }

    /**
     * Returns a String containing the value of the named initialization
     * parameter, or defValue if the parameter does not exist.
     * <p>
     * This method is supplied for convenience. It gets the value of the
     * named parameter from the mailet's MailetConfig object.
     *
     * @param name - a String specifying the name of the initialization parameter
     * @param defValue - a String specifying the default value when the parameter
     *                    is not present
     * @return a String containing the value of the initialization parameter
     */
    public String getInitParameter(String name, String defValue) {
        Preconditions.checkState(config != null, CONFIG_IS_NULL_ERROR_MESSAGE);
        String res = config.getInitParameter(name);
        if (res == null) {
            return defValue;
        } else {
            return res;
        }
    }

    /**
     * Returns the names of the mailet's initialization parameters as an
     * Iterator of String objects, or an empty Iterator if the mailet has no
     * initialization parameters.
     * <p>
     * This method is supplied for convenience. It gets the parameter names from
     * the mailet's MailetConfig object.
     *
     * @return an Iterator of String objects containing the names of
     *          the mailet's initialization parameters
     */
    @Override
    public Iterator<String> getInitParameterNames() {
        Preconditions.checkState(config != null, CONFIG_IS_NULL_ERROR_MESSAGE);
        return config.getInitParameterNames();
    }

    /**
     * Returns this Mailet's MailetConfig object.
     *
     * @return the MailetConfig object that initialized this mailet
     */
    @Override
    public MailetConfig getMailetConfig() {
        return config;
    }

    /**
     * Returns a reference to the MailetContext in which this mailet is
     * running.
     *
     * @return the MailetContext object passed to this mailet by the init method
     */
    @Override
    public MailetContext getMailetContext() {
        return getMailetConfig().getMailetContext();
    }

    /**
     * Returns information about the mailet, such as author, version, and
     * copyright.  By default, this method returns an empty string. Override
     * this method to have it return a meaningful value.
     *
     * @return information about this mailet, by default an empty string
     */
    @Override
    public String getMailetInfo() {
        return "";
    }

    /**
     * Returns the name of this mailet instance.
     *
     * @return the name of this mailet instance
     */
    @Override
    public String getMailetName() {
        Preconditions.checkState(config != null, CONFIG_IS_NULL_ERROR_MESSAGE);
        return config.getMailetName();
    }


    /**
     * <p>Called by the mailet container to indicate to a mailet that the
     * mailet is being placed into service.</p>
     *
     * <p>This implementation stores the MailetConfig object it receives from
     * the mailet container for later use. When overriding this form of the
     * method, call super.init(config).</p>
     *
     * @param newConfig - the MailetConfig object that contains
     *          configuration information for this mailet
     * @throws MessagingException
     *          if an exception occurs that interrupts the mailet's normal operation
     */
    @Override
    public void init(MailetConfig newConfig) throws MessagingException {
        config = newConfig;
        init();
    }

    /**
     * <p>A convenience method which can be overridden so that there's no
     * need to call super.init(config).</p>
     *
     * Instead of overriding init(MailetConfig), simply override this
     * method and it will be called by GenericMailet.init(MailetConfig config).
     * The MailetConfig object can still be retrieved via getMailetConfig().
     *
     * @throws MessagingException
     *          if an exception occurs that interrupts the mailet's normal operation
     */
    public void init() throws MessagingException {
        //Do nothing... can be overridden
    }

    /**
     * Writes the specified message to a mailet log file.
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
     * exception to the mailet log file.
     *
     * @param message - a String that describes the error or exception
     * @param t - the java.lang.Throwable to be logged
     * @deprecated Prefer using SLF4J LoggingFactory to get a Logger in each class
     */
    @Deprecated
    public void log(String message, Throwable t) {
        LOGGER.error(message, t);
    }

    /**
     * <p>Called by the mailet container to allow the mailet to process a
     * message.</p>
     *
     * <p>This method is declared abstract so subclasses must override it.</p>
     *
     * @param mail - the Mail object that contains the MimeMessage and
     *          routing information
     * @throws MessagingException - if an exception occurs that interferes with the mailet's normal operation
     */
    @Override
    public abstract void service(Mail mail) throws MessagingException;
    
    
    
    /**
     * Utility method: Checks if there are disallowed init parameters specified in the
     * configuration file against the allowedInitParameters.
     *
     * @param allowed Set of strings containing the allowed parameter names
     * @throws MessagingException if an unknown parameter name is found
     */
    protected final void checkInitParameters(Set<String> allowed) throws MessagingException {
        Set<String> bad = Streams.stream(getInitParameterNames())
            .filter(not(allowed::contains))
            .filter(not(ERROR_PARAMETERS::contains))
            .collect(ImmutableSet.toImmutableSet());

        if (!bad.isEmpty()) {
            throw new MessagingException("Unexpected init parameters found: " + bad);
        }
    }

}
