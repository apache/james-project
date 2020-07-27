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

package org.apache.james.user.ldap.retry.api;


/**
 * <code>RetryHandler</code>
 */
public interface RetryHandler {

    /**
     * @return the result of invoking an operation
     * @throws Exception
     */
    Object perform() throws Exception;

    /**
     * A hook invoked each time an operation fails if a retry is scheduled
     *
     * @param ex
     *      The <code>Exception</code> thrown when the operation was invoked
     * @param retryCount
     *      The number of times  
     */
    void postFailure(Exception ex, int retryCount);

    /**
     * Encapsulates desired behaviour
     * @return The result of performing the behaviour
     * @throws Exception
     */
    Object operation() throws Exception;

}