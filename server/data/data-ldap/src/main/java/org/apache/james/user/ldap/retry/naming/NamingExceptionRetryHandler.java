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

import javax.naming.NamingException;

import org.apache.james.user.ldap.retry.ExceptionRetryHandler;
import org.apache.james.user.ldap.retry.api.ExceptionRetryingProxy;
import org.apache.james.user.ldap.retry.api.RetrySchedule;

/**
 * Abstract class <code>NamingExceptionRetryHandler</code> narrows the set of Exceptions throwable 
 * by <code>perform</code> to <code>NamingException</code> and its subclasses.
 * <p><code>RuntimeException</code>s are <strong>not</strong> retried.
 * 
 * @see org.apache.james.user.ldap.retry.ExceptionRetryHandler
 * 
 */
public abstract class NamingExceptionRetryHandler extends ExceptionRetryHandler {

    /**
     * Creates a new instance of NamingExceptionRetryHandler.
     *
     * @param exceptionClasses
     * @param proxy
     * @param schedule
     * @param maxRetries
     */
    public NamingExceptionRetryHandler(Class<?>[] exceptionClasses, ExceptionRetryingProxy proxy,
            RetrySchedule schedule, int maxRetries) {
        super(exceptionClasses, proxy, schedule, maxRetries);
    }

    @Override
    public Object perform() throws NamingException {
        try {
            return super.perform();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            // Should only ever be a NamingException
            throw ((NamingException) ex);
        }
    }

    @Override
    public void postFailure(Exception ex, int retryCount) {
        postFailure(((NamingException) ex), retryCount);        
    }

    public void postFailure(NamingException ex, int retryCount) {
        // no-op
    }
}
