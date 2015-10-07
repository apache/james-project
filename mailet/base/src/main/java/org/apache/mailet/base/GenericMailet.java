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

import javax.mail.MessagingException;

import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.MailetContext;
import org.apache.mailet.MailetContext.LogLevel;

import java.util.*;

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
    private MailetConfig config = null;

    /**
     * Called by the mailer container to indicate to a mailet that the
     * mailet is being taken out of service.
     */
    public void destroy() {
        //Do nothing
    }

    /**
     * <p>Gets a boolean valued init parameter.</p>
     * <p>A convenience method. The result is parsed
     * from the value of the named parameter in the {@link MailetConfig}.</p>
     * @param name name of the init parameter to be queried
     * @param defaultValue this value will be substituted when the named value
     * cannot be parse or when the init parameter is absent
     * @return true when the init parameter is <code>true</code> (ignoring case);
     * false when the init parameter is <code>false</code> (ignoring case);
     * otherwise the default value
     * @throws NullPointerException before {@link #init(MailetConfig)}
     */
    public boolean getInitParameter(String name, boolean defaultValue) {
        if (config == null) {
            throw new NullPointerException("Mailet configuration must be set before getInitParameter is called.");
        }
        return MailetUtil.getInitParameter(config, name, defaultValue);
    }
    
    /**
     * Returns a String containing the value of the named initialization
     * parameter, or null if the parameter does not exist.
     * <p>
     * This method is supplied for convenience. It gets the value of the
     * named parameter from the mailet's MailetConfig object.
     *
     * @param name - a String specifying the name of the initialization parameter
     * @return a String containing the value of the initalization parameter
     */
    public String getInitParameter(String name) {
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
     * @return a String containing the value of the initalization parameter
     */
    public String getInitParameter(String name, String defValue) {
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
    public Iterator<String> getInitParameterNames() {
        return config.getInitParameterNames();
    }

    /**
     * Returns this Mailet's MailetConfig object.
     *
     * @return the MailetConfig object that initialized this mailet
     */
    public MailetConfig getMailetConfig() {
        return config;
    }

    /**
     * Returns a reference to the MailetContext in which this mailet is
     * running.
     *
     * @return the MailetContext object passed to this mailet by the init method
     */
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
    public String getMailetInfo() {
        return "";
    }

    /**
     * Returns the name of this mailet instance.
     *
     * @return the name of this mailet instance
     */
    public String getMailetName() {
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
     *          configutation information for this mailet
     * @throws MessagingException
     *          if an exception occurs that interrupts the mailet's normal operation
     */
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
        //Do nothing... can be overriden
    }

    /**
     * Writes the specified message to a mailet log file.
     *
     * @param message - a String specifying the message to be written to the log file
     */
    public void log(String message) {
        getMailetContext().log(LogLevel.INFO, message);
    }

    /**
     * Writes an explanatory message and a stack trace for a given Throwable
     * exception to the mailet log file.
     *
     * @param message - a String that describes the error or exception
     * @param t - the java.lang.Throwable to be logged
     */
    public void log(String message, Throwable t) {
        getMailetContext().log(LogLevel.ERROR, message, t);
    }

    /**
     * <p>Called by the mailet container to allow the mailet to process a
     * message.</p>
     *
     * <p>This method is declared abstract so subclasses must override it.</p>
     *
     * @param mail - the Mail object that contains the MimeMessage and
     *          routing information
     * @throws javax.mail.MessagingException - if an exception occurs that interferes with the mailet's normal operation
     */
    public abstract void service(Mail mail) throws javax.mail.MessagingException;
    
    
    
    /**
     * Utility method: Checks if there are unallowed init parameters specified in the 
     * configuration file against the String[] allowedInitParameters.
     * @param allowedArray array of strings containing the allowed parameter names
     * @throws MessagingException if an unknown parameter name is found
     */
    protected final void checkInitParameters(String[] allowedArray) throws MessagingException {
        // if null then no check is requested
        if (allowedArray == null) {
            return;
        }
        
        Collection<String> allowed = new HashSet<String>();
        Collection<String> bad = new ArrayList<String>();

        Collections.addAll(allowed, allowedArray);
        
        Iterator<String> iterator = getInitParameterNames();
        while (iterator.hasNext()) {
            String parameter = iterator.next();
            if (!allowed.contains(parameter)) {
                bad.add(parameter);
            }
        }
        
        if (bad.size() > 0) {
            throw new MessagingException("Unexpected init parameters found: "
                    + arrayToString(bad.toArray()));
        }
    }
    
    /**
     * Utility method for obtaining a string representation of an array of Objects.
     */
    protected final String arrayToString(Object[] array) {
        if (array == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(1024);
        sb.append("[");
        for (int i = 0; i < array.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(array[i]);
        }
        sb.append("]");
        return sb.toString();
    }

}


