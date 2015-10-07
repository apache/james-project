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

package org.apache.james.mailetcontainer.impl.camel;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.mailetcontainer.api.mock.MockMailetContext;
import org.apache.james.mailetcontainer.api.mock.MockMailetLoader;
import org.apache.james.mailetcontainer.api.mock.MockMatcherLoader;
import org.apache.james.mailetcontainer.lib.AbstractStateMailetProcessor;
import org.apache.james.mailetcontainer.lib.AbstractStateMailetProcessorTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamelMailetProcessorTest extends AbstractStateMailetProcessorTest {

    @Override
    protected AbstractStateMailetProcessor createProcessor(HierarchicalConfiguration configuration) throws Exception {
        CamelMailetProcessor processor = null;
        try {
            processor = new CamelMailetProcessor();
            Logger log = LoggerFactory.getLogger("MockLog");
            // slf4j can't set programmatically any log level. It's just a
            // facade
            // log.setLevel(SimpleLog.LOG_LEVEL_DEBUG);
            processor.setLog(log);
            processor.setCamelContext(new DefaultCamelContext());
            processor.setMailetContext(new MockMailetContext());
            processor.setMailetLoader(new MockMailetLoader());
            processor.setMatcherLoader(new MockMatcherLoader());
            processor.configure(configuration);
            processor.init();
            return processor;
        } finally {
            if (processor != null)
                processor.destroy();
        }
    }

}
