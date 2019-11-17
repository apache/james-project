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

package org.apache.james.mailetcontainer.api.jmx;

/**
 * MBean for Mail processing components
 */
public interface MailProcessorManagementMBean {

    /**
     * Return the count of handled mail
     */
    long getHandledMailCount();

    /**
     * Return the time in ms of the fastest processing
     */
    long getFastestProcessing();

    /**
     * Return the time in ms of the slowest processing
     */
    long getSlowestProcessing();

    /**
     * Return the count of how many time the processing was done without and
     * error
     */
    long getSuccessCount();

    /**
     * Return the count of how many times an error was detected while processing
     */
    long getErrorCount();

    /**
     * Return the time in ms of the last processing
     */
    long getLastProcessing();

}
