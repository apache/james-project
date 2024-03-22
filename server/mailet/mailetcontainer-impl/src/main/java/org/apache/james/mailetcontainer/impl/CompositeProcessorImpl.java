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

package org.apache.james.mailetcontainer.impl;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.mailetcontainer.api.MailProcessor;
import org.apache.james.mailetcontainer.api.MailetLoader;
import org.apache.james.mailetcontainer.api.MatcherLoader;
import org.apache.james.mailetcontainer.lib.AbstractStateCompositeProcessor;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetContext;

/**
 * Build up the Camel Routes by parsing the mailetcontainer.xml configuration
 * file.
 * 
 * It also offer the {@link AbstractStateCompositeProcessor} implementation
 * which allow to inject {@link Mail} into the routes.
 */
public class CompositeProcessorImpl extends AbstractStateCompositeProcessor {

    private final MetricFactory metricFactory;
    private final MailetContext mailetContext;
    private final MatcherLoader matcherLoader;
    private final MailetLoader mailetLoader;

    @Inject
    CompositeProcessorImpl(MetricFactory metricFactory, MailetContext mailetContext, MatcherLoader matcherLoader, MailetLoader mailetLoader) {
        this.metricFactory = metricFactory;
        this.mailetContext = mailetContext;
        this.matcherLoader = matcherLoader;
        this.mailetLoader = mailetLoader;
    }

    @Override
    @PostConstruct
    public void init() throws Exception {
        super.init();
    }

    @Override
    protected MailProcessor createMailProcessor(String name, HierarchicalConfiguration<ImmutableNode> config) throws Exception {
        MailetProcessorImpl processor = new MailetProcessorImpl(metricFactory);
        try {
            processor.setMailetContext(mailetContext);
            processor.setMailetLoader(mailetLoader);
            processor.setMatcherLoader(matcherLoader);
            processor.configure(config);
            processor.setRootMailProcessor(this);
            processor.init();
            return processor;
        } catch (Exception e) {
            processor.destroy();

            throw e;
        }
    }

}
