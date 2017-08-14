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
package org.apache.james.container.spring.lifecycle;

import org.slf4j.Logger;

/**
 * Provide {@link Logger} instances for Beans
 *
 * @deprecated Prefer using SLF4J LoggingFactory to get a Logger in each class
 */
@Deprecated
public interface LogProvider {

    /**
     * Return the Log for the bean with the given name
     * 
     * @param beanName
     * @return log
     */
    Logger getLog(String beanName);

    /**
     * Register a {@link Logger} for a beanName. The registered Log will get
     * returned by {@link #getLog(String)}
     * 
     * @param beanName
     * @param log
     */
    void registerLog(String beanName, Logger log);
}
