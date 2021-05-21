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

import static org.apache.james.mailetcontainer.impl.camel.MatcherSplitter.MATCHER_MATCHED_ATTRIBUTE;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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
import org.apache.james.core.MailAddress;
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

    public static class ProcessingReference {
        public static ProcessingReference newReference(Mail mail) {
            return new ProcessingReference(mail, new AtomicLong(1));
        }

        private final Mail mail;
        private final AtomicLong inFlight;

        private ProcessingReference(Mail mail, AtomicLong inFlight) {
            this.mail = mail;
            this.inFlight = inFlight;
        }

        public ProcessingReference splitForAddresses(List<MailAddress> addresses2) throws MessagingException {
            Mail newMail = mail.duplicate();
            newMail.setState(mail.getState());
            newMail.setRecipients(addresses2);
            inFlight.incrementAndGet();
            return new ProcessingReference(newMail, inFlight);
        }

        public Mail getMail() {
            return mail;
        }

        /**
         * Return true if this reference was the last one on this ongoing processing
         */
        public boolean dispose() throws MessagingException {
            LifecycleUtil.dispose(mail.getMessage());
            LifecycleUtil.dispose(mail);

            return inFlight.decrementAndGet() == 0;
        }

        /**
         * Return true if this reference was the last one on this ongoing processing
         */
        public boolean complete() {
            return inFlight.decrementAndGet() == 0;
        }
    }

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
            if (mail != null) {
                producerTemplate.sendBody(getEndpoint(), ProcessingReference.newReference(mail));
            }
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
            ProcessingReference processingReference = exchange.getIn().getBody(ProcessingReference.class);
            Mail mail = processingReference.mail;
            if (mail.getState().equals(container.getState())) {
                terminatingMailetProcessor.process(mail);
            }
            if (mail.getState().equals(Mail.GHOST)) {
                dispose(exchange, processingReference);
            }
            complete(exchange, container, processingReference);
        }

        private void handleMailet(Exchange exchange, CamelMailetProcessor container, CamelProcessor mailetProccessor) throws Exception {
            ProcessingReference processingReference = exchange.getIn().getBody(ProcessingReference.class);
            Mail mail = processingReference.mail;
            boolean isMatched = mail.removeAttribute(MATCHER_MATCHED_ATTRIBUTE).isPresent();
            if (isMatched) {
                mailetProccessor.process(mail);
            } else {
                LOGGER.info("{} were not matched ", ImmutableList.copyOf(mail.getRecipients()));
            }
            if (mail.getState().equals(Mail.GHOST)) {
                LOGGER.info("Abort processing {}", ImmutableList.copyOf(mail.getRecipients()));
                dispose(exchange, processingReference);
                return;
            }
            if (!mail.getState().equals(container.getState())) {
                container.toProcessor(mail);
                complete(exchange, container, processingReference);
            }
        }

        private void complete(Exchange exchange, CamelMailetProcessor container, ProcessingReference processingReference) {
            LOGGER.debug("End of mailetprocessor for state {} reached", container.getState());
            if (processingReference.complete()) {
                exchange.setRouteStop(true);
            }
        }

        private void dispose(Exchange exchange, ProcessingReference processingReference) throws MessagingException {
            if (processingReference.dispose()) {
                // stop routing
                exchange.setRouteStop(true);
            }
        }

    }

}
