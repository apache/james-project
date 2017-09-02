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

import org.apache.james.lifecycle.api.LogEnabled;

/**
 * Inject Commons Log to beans which implement LogEnabled.
 *
 * @deprecated Prefer using SLF4J LoggingFactory to get a Logger in each class
 */
@Deprecated
public class LogEnabledBeanPostProcessor extends AbstractLifecycleBeanPostProcessor<LogEnabled> {

    private LogProvider provider;

    public void setLogProvider(LogProvider provider) {
        this.provider = provider;
    }

    @Override
    protected Class<LogEnabled> getLifeCycleInterface() {
        return LogEnabled.class;
    }

    @Override
    protected void executeLifecycleMethodBeforeInit(LogEnabled bean, String beanname) throws Exception {
        bean.setLog(provider.getLog(beanname));
    }

    @Override
    protected void executeLifecycleMethodAfterInit(LogEnabled bean, String beanname) throws Exception {
        // Do nothing.
    }

}
