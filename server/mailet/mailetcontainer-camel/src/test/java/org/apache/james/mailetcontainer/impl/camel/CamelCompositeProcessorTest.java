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
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.mailetcontainer.api.mock.MockMailetLoader;
import org.apache.james.mailetcontainer.api.mock.MockMatcherLoader;
import org.apache.james.mailetcontainer.lib.AbstractStateCompositeProcessor;
import org.apache.james.mailetcontainer.lib.AbstractStateCompositeProcessorTest;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.mailet.base.test.FakeMailContext;

public class CamelCompositeProcessorTest extends AbstractStateCompositeProcessorTest {

    @Override
    protected AbstractStateCompositeProcessor createProcessor(HierarchicalConfiguration<ImmutableNode> config) throws Exception {
        CamelCompositeProcessor processor = new CamelCompositeProcessor(new RecordingMetricFactory(),
            FakeMailContext.defaultContext(),
            new MockMatcherLoader(),
            new MockMailetLoader());
        try {
            processor.setCamelContext(new DefaultCamelContext());
            processor.configure(config);
            processor.init();
            return processor;
        } catch (Exception e) {
            processor.dispose();
            throw e;
        }

    }

}
