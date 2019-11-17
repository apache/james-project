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

import java.util.List;

import javax.annotation.PostConstruct;
import javax.mail.MessagingException;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.mailetcontainer.impl.MatcherMailetPair;
import org.apache.james.mailetcontainer.lib.AbstractStateMailetProcessor;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

/**
 * {@link org.apache.james.mailetcontainer.lib.AbstractStateMailetProcessor} implementation which use Camel DSL for
 * the {@link Matcher} / {@link Mailet} routing
 */
public class CamelMailetProcessor extends AbstractStateMailetProcessor implements CamelContextAware {
    private static final Logger LOGGER = LoggerFactory.getLogger(CamelMailetProcessor.class);

    private CamelContext context;

    private ProducerTemplate producerTemplate;

    private final MetricFactory metricFactory;
    private List<MatcherMailetPair> pairs;

    public CamelMailetProcessor(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        try {
            producerTemplate.sendBody(getEndpoint(), mail);

        } catch (CamelExecutionException ex) {
            throw new MessagingException("Unable to process mail " + mail.getName(), ex);
        }
    }

    @Override
    public CamelContext getCamelContext() {
        return context;
    }

    @Override
    public void setCamelContext(CamelContext context) {
        this.context = context;
    }

    public List<MatcherMailetPair> getPairs() {
        return ImmutableList.copyOf(pairs);
    }

    /**
     * Return the endpoint for the processorname.
     * 
     * This will return a "direct" endpoint.
     */
    protected String getEndpoint() {
        return "direct:processor." + getState();
    }

    @Override
    @PostConstruct
    public void init() throws Exception {
        producerTemplate = context.createProducerTemplate();

        if (context.getStatus().isStopped()) {
            context.start();
        }
        super.init();
    }

    @Override
    protected void setupRouting(List<MatcherMailetPair> pairs) throws MessagingException {
        try {
            this.pairs = pairs;
            context.addRoutes(new MailetContainerRouteBuilder(this, metricFactory, pairs));
        } catch (Exception e) {
            throw new MessagingException("Unable to setup routing for MailetMatcherPairs", e);
        }
    }

    /**
     * {@link RouteBuilder} which construct the Matcher and Mailet routing use
     * Camel DSL
     */
    private static class MailetContainerRouteBuilder extends RouteBuilder {

        private final CamelMailetProcessor container;

        private final List<MatcherMailetPair> pairs;
        private final MetricFactory metricFactory;

        private MailetContainerRouteBuilder(CamelMailetProcessor container, MetricFactory metricFactory, List<MatcherMailetPair> pairs) {
            this.container = container;
            this.metricFactory = metricFactory;
            this.pairs = pairs;
        }

        @Override
        public void configure() {
            String state = container.getState();
            CamelProcessor terminatingMailetProcessor = new CamelProcessor(metricFactory, container, new TerminatingMailet());

            RouteDefinition processorDef = from(container.getEndpoint())
                .routeId(state)
                .setExchangePattern(ExchangePattern.InOnly);

            for (MatcherMailetPair pair : pairs) {
                CamelProcessor mailetProccessor = new CamelProcessor(metricFactory, container, pair.getMailet());
                MatcherSplitter matcherSplitter = new MatcherSplitter(metricFactory, container, pair);

                processorDef
                        // do splitting of the mail based on the stored matcher
                        .split().method(matcherSplitter)
                            .aggregationStrategy(new UseLatestAggregationStrategy())
                        .process(exchange -> handleMailet(exchange, container, mailetProccessor));
            }

            processorDef
                .process(exchange -> terminateSmoothly(exchange, container, terminatingMailetProcessor));

        }

        private void terminateSmoothly(Exchange exchange, CamelMailetProcessor container, CamelProcessor terminatingMailetProcessor) throws Exception {
            Mail mail = exchange.getIn().getBody(Mail.class);
            if (mail.getState().equals(container.getState())) {
                terminatingMailetProcessor.process(mail);
            }
            if (mail.getState().equals(Mail.GHOST)) {
                dispose(exchange, mail);
            }
            complete(exchange, container);
        }

        private void handleMailet(Exchange exchange, CamelMailetProcessor container, CamelProcessor mailetProccessor) throws Exception {
            Mail mail = exchange.getIn().getBody(Mail.class);
            boolean isMatched = mail.removeAttribute(MatcherSplitter.MATCHER_MATCHED_ATTRIBUTE).isPresent();
            if (isMatched) {
                mailetProccessor.process(mail);
            }
            if (mail.getState().equals(Mail.GHOST)) {
                dispose(exchange, mail);
                return;
            }
            if (!mail.getState().equals(container.getState())) {
                container.toProcessor(mail);
                complete(exchange, container);
            }
        }

        private void complete(Exchange exchange, CamelMailetProcessor container) {
            LOGGER.debug("End of mailetprocessor for state {} reached", container.getState());
            exchange.setProperty(Exchange.ROUTE_STOP, true);
        }

        private void dispose(Exchange exchange, Mail mail) throws MessagingException {
            LifecycleUtil.dispose(mail.getMessage());
            LifecycleUtil.dispose(mail);

            // stop routing
            exchange.setProperty(Exchange.ROUTE_STOP, true);
        }

    }

}
