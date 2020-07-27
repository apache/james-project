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

package org.apache.james.user.ldap.retry.naming.ldap;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.ldap.Control;
import javax.naming.ldap.ExtendedRequest;
import javax.naming.ldap.ExtendedResponse;
import javax.naming.ldap.LdapContext;

import org.apache.james.user.ldap.retry.ExceptionRetryHandler;
import org.apache.james.user.ldap.retry.api.RetrySchedule;
import org.apache.james.user.ldap.retry.naming.LoggingRetryHandler;
import org.apache.james.user.ldap.retry.naming.directory.RetryingDirContext;

/**
 * <code>RetryingLdapContext</code> retries the methods defined by <code>javax.naming.ldap.LdapContext</code>
 * according to the specified schedule. 
 * 
 * @see ExceptionRetryHandler
 * @see org.apache.james.user.ldap.retry.api.ExceptionRetryingProxy
 * @see javax.naming.ldap.LdapContext
 */
public abstract class RetryingLdapContext extends RetryingDirContext implements LdapContext {
   
    /**
     * Creates a new instance of RetryingLdapContext.
     *
     * @param maxRetries
     * @throws NamingException
     */
    public RetryingLdapContext(RetrySchedule schedule, int maxRetries) throws NamingException {
        super(schedule, maxRetries);
    }

    @Override
    public ExtendedResponse extendedOperation(final ExtendedRequest request) throws NamingException {
        return (ExtendedResponse) new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()) {
                @Override
                public Object operation() throws NamingException {
                    return ((LdapContext) getDelegate()).extendedOperation(request);
                }
            }.perform();
    }

    @Override
    public Control[] getConnectControls() throws NamingException {
        return (Control[]) new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()) {
                @Override
                public Object operation() throws NamingException {
                    return ((LdapContext) getDelegate()).getConnectControls();
                }
            }.perform();
    }

    @Override
    public Control[] getRequestControls() throws NamingException {
        return (Control[]) new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()) {
                @Override
                public Object operation() throws NamingException {
                    return ((LdapContext) getDelegate()).getRequestControls();
                }
            }.perform();
    }

    @Override
    public Control[] getResponseControls() throws NamingException {
        return (Control[]) new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()) {
                @Override
                public Object operation() throws NamingException {
                    return ((LdapContext) getDelegate()).getResponseControls();
                }
            }.perform();
    }

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

    @Override
    public void reconnect(final Control[] connCtls) throws NamingException {
        new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()) {
                @Override
                public Object operation() throws NamingException {
                    ((LdapContext) getDelegate()).reconnect(connCtls);
                    return null;
                }
            }.perform();
    }

    @Override
    public void setRequestControls(final Control[] requestControls) throws NamingException {
        new LoggingRetryHandler(DEFAULT_EXCEPTION_CLASSES, this, getSchedule(), getMaxRetries()) {
                @Override
                public Object operation() throws NamingException {
                    ((LdapContext) getDelegate()).setRequestControls(requestControls);
                    return null;
                }
            }.perform();
    }

}
