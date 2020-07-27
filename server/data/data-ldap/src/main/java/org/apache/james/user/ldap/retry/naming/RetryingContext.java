/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.user.ldap.retry.naming;

import java.util.Hashtable;

import javax.naming.Binding;
import javax.naming.CommunicationException;
import javax.naming.Context;
import javax.naming.Name;
import javax.naming.NameClassPair;
import javax.naming.NameParser;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.NoInitialContextException;
import javax.naming.ServiceUnavailableException;

import org.apache.james.user.ldap.retry.ExceptionRetryHandler;
import org.apache.james.user.ldap.retry.api.ExceptionRetryingProxy;
import org.apache.james.user.ldap.retry.api.RetrySchedule;

/**
 * <code>RetryingContext</code> retries the methods defined by <code>javax.naming.Context</code>
 * according to the specified schedule. 
 * 
 * @see ExceptionRetryHandler
 * @see org.apache.james.user.ldap.retry.api.ExceptionRetryingProxy
 * @see javax.naming.Context
 */
public abstract class RetryingContext implements Context, ExceptionRetryingProxy {

    public static final Class<?>[] DEFAULT_EXCEPTION_CLASSES = new Class<?>[] {
            CommunicationException.class,
            ServiceUnavailableException.class,
            NoInitialContextException.class };

    private Context delegate = null;
    private RetrySchedule schedule = null;
    private int maxRetries = 0;

    /**
     * Creates a new instance of RetryingContext.
     * 
     * @throws NamingException
     * 
     */
    private RetryingContext() {
        super();
    }
    
    /**
     * Creates a new instance of RetryingContext using the default exception
     * classes thrown when an external interruption to services on which we
     * depend occurs.
     * 
     * @param schedule
     * @param maxRetries
     * @throws NamingException
     */
    public RetryingContext(RetrySchedule schedule, int maxRetries)
            throws NamingException {
        this(DEFAULT_EXCEPTION_CLASSES, schedule, maxRetries);
    }

    /**
     * Creates a new instance of RetryingContext.
     *
     * @param exceptionClasses
     * @param schedule
     * @param maxRetries
     * @throws NamingException
     */
    public RetryingContext(Class<?>[] exceptionClasses, RetrySchedule schedule, int maxRetries)
            throws NamingException {
        this();
        this.schedule = schedule;
        this.maxRetries = maxRetries;
        this.delegate = (Context) new LoggingRetryHandler(exceptionClasses, this,
                this.schedule, this.maxRetries) {

            @Override
            public Object operation() throws Exception {
                return newDelegate();
            }
        }.perform();
    }

    @Override
    public Object addToEnvironment(final String propName, final Object propVal)
            throws NamingException {
        return new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, schedule, maxRetries
        ) {

            @Override
            public Object operation() throws NamingException {
                return getDelegate().addToEnvironment(propName, propVal);
            }
        }.perform();
    }

    @Override
    public void bind(final Name name, final Object obj) throws NamingException {
        new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, schedule, maxRetries
        ) {

            @Override
            public Object operation() throws NamingException {
                getDelegate().bind(name, obj);
                return null;
            }
        }.perform();
    }

    @Override
    public void bind(final String name, final Object obj) throws NamingException {
        new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, schedule, maxRetries
        ) {

            @Override
            public Object operation() throws NamingException {
                getDelegate().bind(name, obj);
                return null;
            }
        }.perform();
    }

    @Override
    public void close() throws NamingException {
        new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, schedule, maxRetries
        ) {

            @Override
            public Object operation() throws NamingException {
                getDelegate().close();
                return null;
            }
        }.perform();
    }

    @Override
    public Name composeName(final Name name, final Name prefix) throws NamingException {
        return (Name) new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, schedule, maxRetries
        ) {

            @Override
            public Object operation() throws NamingException {
                return getDelegate().composeName(name, prefix);
            }
        }.perform();
    }

    @Override
    public String composeName(final String name, final String prefix) throws NamingException {
        return (String) new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, schedule, maxRetries
        ) {

            @Override
            public Object operation() throws NamingException {
                return getDelegate().composeName(name, prefix);
            }
        }.perform();
    }

    @Override
    public Context createSubcontext(final Name name) throws NamingException {
        final Context context = getDelegate();
        return new RetryingContext(getSchedule(), getMaxRetries()) {

            @Override
            public Context newDelegate() throws NamingException {
                return context.createSubcontext(name);
            }
        };
    }

    @Override
    public Context createSubcontext(final String name) throws NamingException {
        final Context context = getDelegate();
        return new RetryingContext(getSchedule(), getMaxRetries()) {

            @Override
            public Context newDelegate() throws NamingException {
                return context.createSubcontext(name);
            }
        };
    }

    @Override
    public void destroySubcontext(final Name name) throws NamingException {
        new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, schedule, maxRetries
        ) {

            @Override
            public Object operation() throws NamingException {
                getDelegate().destroySubcontext(name);
                return null;
            }
        }.perform();
    }

    @Override
    public void destroySubcontext(final String name) throws NamingException {
        new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, schedule, maxRetries
        ) {

            @Override
            public Object operation() throws NamingException {
                getDelegate().destroySubcontext(name);
                return null;
            }
        }.perform();
    }

    @Override
    public Hashtable<?, ?> getEnvironment() throws NamingException {
        return (Hashtable<?, ?>) new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this,
                schedule, maxRetries) {

            @Override
            public Object operation() throws NamingException {
                return getDelegate().getEnvironment();
            }
        }.perform();
    }

    @Override
    public String getNameInNamespace() throws NamingException {
        return (String) new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, schedule, maxRetries
        ) {

            @Override
            public Object operation() throws NamingException {
                return getDelegate().getNameInNamespace();
            }
        }.perform();
    }

    @Override
    public NameParser getNameParser(final Name name) throws NamingException {
        return (NameParser) new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, schedule, maxRetries
        ) {

            @Override
            public Object operation() throws NamingException {
                return getDelegate().getNameParser(name);
            }
        }.perform();
    }

    @Override
    public NameParser getNameParser(final String name) throws NamingException {
        return (NameParser) new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, schedule, maxRetries
        ) {

            @Override
            public Object operation() throws NamingException {
                return getDelegate().getNameParser(name);
            }
        }.perform();
    }

    @SuppressWarnings("unchecked")
    @Override
    public NamingEnumeration<NameClassPair> list(final Name name) throws NamingException {
        return (NamingEnumeration<NameClassPair>) new LoggingRetryHandler(
                DEFAULT_EXCEPTION_CLASSES, this, schedule, maxRetries) {

            @Override
            public Object operation() throws NamingException {
                return getDelegate().list(name);
            }
        }.perform();
    }

    @SuppressWarnings("unchecked")
    @Override
    public NamingEnumeration<NameClassPair> list(final String name) throws NamingException {
        return (NamingEnumeration<NameClassPair>) new LoggingRetryHandler(
                DEFAULT_EXCEPTION_CLASSES, this, schedule, maxRetries) {

            @Override
            public Object operation() throws NamingException {
                return getDelegate().list(name);
            }
        }.perform();
    }

    @SuppressWarnings("unchecked")
    @Override
    public NamingEnumeration<Binding> listBindings(final Name name) throws NamingException {
        return (NamingEnumeration<Binding>) new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES,
                this, schedule, maxRetries) {

            @Override
            public Object operation() throws NamingException {
                return getDelegate().listBindings(name);
            }
        }.perform();
    }

    @SuppressWarnings("unchecked")
    @Override
    public NamingEnumeration<Binding> listBindings(final String name) throws NamingException {
        return (NamingEnumeration<Binding>) new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES,
                this, schedule, maxRetries) {

            @Override
            public Object operation() throws NamingException {
                return getDelegate().listBindings(name);
            }
        }.perform();
    }

    @Override
    public Object lookup(final Name name) throws NamingException {
        return new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, schedule, maxRetries
        ) {

            @Override
            public Object operation() throws NamingException {
                return getDelegate().lookup(name);
            }
        }.perform();
    }

    @Override
    public Object lookup(final String name) throws NamingException {
        return new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, schedule, maxRetries
        ) {

            @Override
            public Object operation() throws NamingException {
                return getDelegate().lookup(name);
            }
        }.perform();
    }

    @Override
    public Object lookupLink(final Name name) throws NamingException {
        return new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, schedule, maxRetries
        ) {

            @Override
            public Object operation() throws NamingException {
                return getDelegate().lookupLink(name);
            }
        }.perform();
    }

    @Override
    public Object lookupLink(final String name) throws NamingException {
        return new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, schedule, maxRetries
        ) {

            @Override
            public Object operation() throws NamingException {
                return getDelegate().lookupLink(name);
            }
        }.perform();
    }

    @Override
    public void rebind(final Name name, final Object obj) throws NamingException {
        new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, schedule, maxRetries
        ) {

            @Override
            public Object operation() throws NamingException {
                getDelegate().rebind(name, obj);
                return null;
            }
        }.perform();
    }

    @Override
    public void rebind(final String name, final Object obj) throws NamingException {
        new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, schedule, maxRetries
        ) {

            @Override
            public Object operation() throws NamingException {
                getDelegate().rebind(name, obj);
                return null;
            }
        }.perform();
    }

    @Override
    public Object removeFromEnvironment(final String propName) throws NamingException {
        return new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, schedule, maxRetries
        ) {

            @Override
            public Object operation() throws NamingException {
                return getDelegate().removeFromEnvironment(propName);
            }
        }.perform();
    }

    @Override
    public void rename(final Name oldName, final Name newName) throws NamingException {
        new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, schedule, maxRetries
        ) {

            @Override
            public Object operation() throws NamingException {
                getDelegate().rename(oldName, newName);
                return null;
            }
        }.perform();
    }

    @Override
    public void rename(final String oldName, final String newName) throws NamingException {
        new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, schedule, maxRetries
        ) {

            @Override
            public Object operation() throws NamingException {
                getDelegate().rename(oldName, newName);
                return null;
            }
        }.perform();
    }

    @Override
    public void unbind(final Name name) throws NamingException {
        new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, schedule, maxRetries
        ) {

            @Override
            public Object operation() throws NamingException {
                getDelegate().unbind(name);
                return null;
            }
        }.perform();
    }

    @Override
    public void unbind(final String name) throws NamingException {
        new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, schedule, maxRetries) {

            @Override
            public Object operation() throws NamingException {
                getDelegate().unbind(name);
                return null;
            }
        }.perform();
    }

    @Override
    public Context getDelegate() {
        return delegate;
    }
    
    
    @Override
    public void resetDelegate() throws Exception {
        if (null != delegate) {
            delegate.close();
        }
        delegate = (Context)newDelegate();
    }
   
    /**
     * @return the schedule
     */
    public RetrySchedule getSchedule() {
        return schedule;
    }
    
    /**
     * @return the maxRetries
     */
    public int getMaxRetries() {
        return maxRetries;
    }

}
