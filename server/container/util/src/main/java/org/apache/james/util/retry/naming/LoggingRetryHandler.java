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

package org.apache.james.util.retry.naming;

import javax.naming.NamingException;

import org.apache.james.util.retry.api.ExceptionRetryingProxy;
import org.apache.james.util.retry.api.RetrySchedule;
import org.slf4j.Logger;

/**
 * Abstract class <code>LoggingRetryHandler</code> implements logging of failures 
 */
abstract public class LoggingRetryHandler extends NamingExceptionRetryHandler {
    
    private Logger _logger = null;

    /**
     * Creates a new instance of LoggingRetryHandler.
     *
     * @param exceptionClasses
     * @param proxy
     * @param maxRetries
     * @param logger
     */
    public LoggingRetryHandler(Class<?>[] exceptionClasses, ExceptionRetryingProxy proxy,
            RetrySchedule schedule, int maxRetries, Logger logger) {
        super(exceptionClasses, proxy, schedule, maxRetries);
        _logger = logger;
    }

    /**
     */
    @Override
    public void postFailure(NamingException ex, int retryCount) {
        super.postFailure(ex, retryCount);
        _logger.info(
                "Retry failure: " + ex.getLocalizedMessage() + "\n Retrying in " + getRetryInterval(retryCount) / 1000 + " seconds"
                );
    }

}
