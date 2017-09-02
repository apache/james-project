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

package org.apache.james.util.retry.naming.ldap;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.ldap.Control;
import javax.naming.ldap.ExtendedRequest;
import javax.naming.ldap.ExtendedResponse;
import javax.naming.ldap.LdapContext;

import org.apache.james.util.retry.api.RetrySchedule;
import org.apache.james.util.retry.naming.LoggingRetryHandler;
import org.apache.james.util.retry.naming.directory.RetryingDirContext;

/**
 * <code>RetryingLdapContext</code> retries the methods defined by <code>javax.naming.ldap.LdapContext</code>
 * according to the specified schedule. 
 * 
 * @see org.apache.james.util.retry.ExceptionRetryHandler
 * @see org.apache.james.util.retry.api.ExceptionRetryingProxy
 * @see javax.naming.ldap.LdapContext
 */
abstract public class RetryingLdapContext extends RetryingDirContext implements LdapContext {
   
    /**
     * Creates a new instance of RetryingLdapContext.
     *
     * @param maxRetries
     * @throws NamingException
     */
    public RetryingLdapContext(RetrySchedule schedule, int maxRetries) throws NamingException {
        super(schedule, maxRetries);
    }

    /**
     * @see javax.naming.ldap.LdapContext#extendedOperation(javax.naming.ldap.ExtendedRequest)
     */
    @Override
    public ExtendedResponse extendedOperation(final ExtendedRequest request) throws NamingException {
        return (ExtendedResponse) new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()){

            @Override
            public Object operation() throws NamingException {
                return ((LdapContext) getDelegate()).extendedOperation(request);
            }}.perform();
    }

    /**
     * @see javax.naming.ldap.LdapContext#getConnectControls()
     */
    @Override
    public Control[] getConnectControls() throws NamingException {
        return (Control[]) new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()){

            @Override
            public Object operation() throws NamingException {
                return ((LdapContext) getDelegate()).getConnectControls();
            }}.perform();
    }

    /**
     * @see javax.naming.ldap.LdapContext#getRequestControls()
     */
    @Override
    public Control[] getRequestControls() throws NamingException {
        return (Control[]) new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()){

            @Override
            public Object operation() throws NamingException {
                return ((LdapContext) getDelegate()).getRequestControls();
            }}.perform();
    }

    /**
     * @see javax.naming.ldap.LdapContext#getResponseControls()
     */
    @Override
    public Control[] getResponseControls() throws NamingException {
        return (Control[]) new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()){

            @Override
            public Object operation() throws NamingException {
                return ((LdapContext) getDelegate()).getResponseControls();
            }}.perform();
    }

    /**
     * @see javax.naming.ldap.LdapContext#newInstance(javax.naming.ldap.Control[])
     */
    @Override
    public LdapContext newInstance(final Control[] requestControls) throws NamingException {
        final Context context = getDelegate();
        return new RetryingLdapContext(getSchedule(), getMaxRetries()) {

            @Override
            public Context newDelegate() throws NamingException {
                return ((LdapContext) context).newInstance(requestControls);
            }
        };
    }

    /**
     * @see javax.naming.ldap.LdapContext#reconnect(javax.naming.ldap.Control[])
     */
    @Override
    public void reconnect(final Control[] connCtls) throws NamingException {
        new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()){

            @Override
            public Object operation() throws NamingException {
                ((LdapContext) getDelegate()).reconnect(connCtls);
                return null;
            }}.perform();
    }

    /**
     * @see javax.naming.ldap.LdapContext#setRequestControls(javax.naming.ldap.Control[])
     */
    @Override
    public void setRequestControls(final Control[] requestControls) throws NamingException {
        new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()){

            @Override
            public Object operation() throws NamingException {
                ((LdapContext) getDelegate()).setRequestControls(requestControls);
                return null;
            }}.perform();
    }

}
