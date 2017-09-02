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
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.james.mailetcontainer.impl.MailetConfigImpl;
import org.apache.james.mailetcontainer.impl.MatcherMailetPair;
import org.apache.james.mailetcontainer.lib.AbstractStateMailetProcessor;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.MailetConfig;
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

    private final UseLatestAggregationStrategy aggr = new UseLatestAggregationStrategy();
    private final MetricFactory metricFactory;
    private List<MatcherMailetPair> pairs;

    public CamelMailetProcessor(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
    }

    /**
     * @see
     * org.apache.james.mailetcontainer.api.MailProcessor#service(org.apache.mailet.Mail)
     */
    public void service(Mail mail) throws MessagingException {
        try {
            producerTemplate.sendBody(getEndpoint(), mail);

        } catch (CamelExecutionException ex) {
            throw new MessagingException("Unable to process mail " + mail.getName(), ex);
        }
    }

    /**
     * @see org.apache.camel.CamelContextAware#getCamelContext()
     */
    public CamelContext getCamelContext() {
        return context;
    }

    /**
     * @see org.apache.camel.CamelContextAware#setCamelContext(org.apache.camel.CamelContext)
     */
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
     * 
     * @return endPoint
     */
    protected String getEndpoint() {
        return "direct:processor." + getState();
    }

    @PostConstruct
    public void init() throws Exception {
        producerTemplate = context.createProducerTemplate();

        if (context.getStatus().isStopped()) {
            context.start();
        }
        super.init();
    }

    /**
     * @see
     * org.apache.james.mailetcontainer.lib.AbstractStateMailetProcessor#setupRouting
     * (java.util.List)
     */
    protected void setupRouting(List<MatcherMailetPair> pairs) throws MessagingException {
        try {
            this.pairs = pairs;
            context.addRoutes(new MailetContainerRouteBuilder(pairs));
        } catch (Exception e) {
            throw new MessagingException("Unable to setup routing for MailetMatcherPairs", e);
        }
    }

    /**
     * {@link RouteBuilder} which construct the Matcher and Mailet routing use
     * Camel DSL
     */
    private final class MailetContainerRouteBuilder extends RouteBuilder {

        private final List<MatcherMailetPair> pairs;

        public MailetContainerRouteBuilder(List<MatcherMailetPair> pairs) {
            this.pairs = pairs;
        }

        @Override
        public void configure() throws Exception {
            Processor disposeProcessor = new DisposeProcessor();
            Processor removePropsProcessor = new RemovePropertiesProcessor();
            Processor completeProcessor = new CompleteProcessor();
            Processor stateChangedProcessor = new StateChangedProcessor();

            String state = getState();

            RouteDefinition processorDef = from(getEndpoint()).routeId(state).setExchangePattern(ExchangePattern.InOnly)
            // store the logger in properties
                    .setProperty(MatcherSplitter.LOGGER_PROPERTY, constant(LOGGER))
                    .setProperty(MatcherSplitter.METRIC_FACTORY, constant(metricFactory));

            for (MatcherMailetPair pair : pairs) {
                Matcher matcher = pair.getMatcher();
                Mailet mailet = pair.getMailet();

                String onMatchException = null;
                MailetConfig mailetConfig = mailet.getMailetConfig();

                if (mailetConfig instanceof MailetConfigImpl) {
                    onMatchException = ((MailetConfigImpl) mailetConfig).getInitAttribute("onMatchException");
                }

                CamelProcessor mailetProccessor = new CamelProcessor(metricFactory, mailet, CamelMailetProcessor.this);
                // Store the matcher to use for splitter in properties
                processorDef.setProperty(MatcherSplitter.MATCHER_PROPERTY, constant(matcher)).setProperty(MatcherSplitter.ON_MATCH_EXCEPTION_PROPERTY, constant(onMatchException)).setProperty(MatcherSplitter.MAILETCONTAINER_PROPERTY, constant(CamelMailetProcessor.this))

                        // do splitting of the mail based on the stored matcher
                        .split().method(MatcherSplitter.class).aggregationStrategy(aggr)

                        .choice().when(new MatcherMatch()).process(mailetProccessor).end()

                        .choice().when(new MailStateEquals(Mail.GHOST)).process(disposeProcessor).stop().otherwise().process(removePropsProcessor).end()

                        .choice().when(new MailStateNotEquals(state)).process(stateChangedProcessor).process(completeProcessor).stop().end();
            }

            Processor terminatingMailetProcessor = new CamelProcessor(metricFactory, new TerminatingMailet(), CamelMailetProcessor.this);

            processorDef
            // start choice
                    .choice()

                    // when the mail state did not change till yet ( the end of
                    // the route) we need to call the TerminatingMailet to
                    // make sure we don't fall into a endless loop
                    .when(new MailStateEquals(state)).process(terminatingMailetProcessor).stop()

                    // dispose when needed
                    .when(new MailStateEquals(Mail.GHOST)).process(disposeProcessor).stop()

                    // this container is complete
                    .otherwise().process(completeProcessor).stop();

        }

        private final class RemovePropertiesProcessor implements Processor {

            public void process(Exchange exchange) throws Exception {
                exchange.removeProperty(MatcherSplitter.ON_MATCH_EXCEPTION_PROPERTY);
                exchange.removeProperty(MatcherSplitter.MATCHER_PROPERTY);
            }
        }

        private final class CompleteProcessor implements Processor {

            public void process(Exchange ex) throws Exception {
                LOGGER.debug("End of mailetprocessor for state " + getState() + " reached");
                ex.setProperty(Exchange.ROUTE_STOP, true);
            }
        }

        private final class StateChangedProcessor implements Processor {

            public void process(Exchange arg0) throws Exception {
                Mail mail = arg0.getIn().getBody(Mail.class);
                toProcessor(mail);

            }

        }

    }

}
