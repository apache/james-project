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

import org.apache.james.user.ldap.retry.api.ExceptionRetryingProxy;
import org.apache.james.user.ldap.retry.api.RetrySchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract class <code>LoggingRetryHandler</code> implements logging of failures 
 */
public abstract class LoggingRetryHandler extends NamingExceptionRetryHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingRetryHandler.class);

    /**
     * Creates a new instance of LoggingRetryHandler.
     *
     * @param exceptionClasses
     * @param proxy
     * @param maxRetries
     */
    public LoggingRetryHandler(Class<?>[] exceptionClasses, ExceptionRetryingProxy proxy,
                               RetrySchedule schedule, int maxRetries) {
        super(exceptionClasses, proxy, schedule, maxRetries);
    }

    @Override
    public void postFailure(NamingException ex, int retryCount) {
        super.postFailure(ex, retryCount);
        LOGGER.info("Retry failure. Retrying in {} seconds", getRetryInterval(retryCount) / 1000, ex);
    }

}
