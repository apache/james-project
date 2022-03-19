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

import static org.apache.james.mailetcontainer.impl.MatcherSplitter.MATCHER_MATCHED_ATTRIBUTE;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import jakarta.mail.MessagingException;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.lifecycle.api.LifecycleUtil;
import org.apache.james.mailetcontainer.lib.AbstractStateMailetProcessor;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.mailet.Mail;
import org.apache.mailet.Mailet;
import org.apache.mailet.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

/**
 * {@link org.apache.james.mailetcontainer.lib.AbstractStateMailetProcessor} implementation which use Camel DSL for
 * the {@link Matcher} / {@link Mailet} routing
 */
public class MailetProcessorImpl extends AbstractStateMailetProcessor {
    private static class ProcessingStep {
        private static class Builder {
            @FunctionalInterface
            interface RequiresInFlight {
                RequiresEncounteredMails inFlight(ImmutableList<Mail> inFlightMails);
            }

            @FunctionalInterface
            interface RequiresEncounteredMails {
                ProcessingStep encountered(ImmutableList<Mail> inFlightMails);
            }
        }

        public static ProcessingStep initial(Mail mail) {
            return new ProcessingStep(ImmutableList.of(mail), ImmutableSet.of(mail));
        }

        private ImmutableList<Mail> inFlightMails;
        private ImmutableSet<Mail> encounteredMails;

        private ProcessingStep(ImmutableList<Mail> inFlightMails, ImmutableSet<Mail> encounteredMails) {
            this.inFlightMails = inFlightMails;
            this.encounteredMails = encounteredMails;
        }

        public ImmutableList<Mail> getInFlightMails() {
            return inFlightMails;
        }

        public Builder.RequiresInFlight nextStepBuilder() {
            return inFlight -> encountered -> new ProcessingStep(inFlight,
                ImmutableSet.<Mail>builder()
                    .addAll(inFlight)
                    .addAll(encounteredMails)
                    .addAll(encountered)
                    .build());
        }

        public void ghostInFlight(Consumer<Mail> callback) {
            inFlightMails
                .stream()
                .filter(mail -> !mail.getState().equals(Mail.GHOST))
                .forEach(mail -> {
                    callback.accept(mail);
                    mail.setState(Mail.GHOST);
                });
        }

        public void disposeGhostedEncounteredMails() {
            encounteredMails
                .stream()
                .filter(mail -> mail.getState().equals(Mail.GHOST))
                .forEach(Throwing.<Mail>consumer(mail -> {
                    LifecycleUtil.dispose(mail);
                    LifecycleUtil.dispose(mail.getMessage());
                }).sneakyThrow());
        }

        public boolean test() {
            return inFlightMails.size() > 0;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(MailetProcessorImpl.class);

    private final MetricFactory metricFactory;
    private List<MatcherMailetPair> pairs;
    private Map<MatcherSplitter, ProcessorImpl> pairsToBeProcessed;

    public MailetProcessorImpl(MetricFactory metricFactory) {
        this.metricFactory = metricFactory;
    }

    @Override
    public void service(Mail mail) {
        ProcessingStep lastStep = pairsToBeProcessed.entrySet().stream()
            .reduce(ProcessingStep.initial(mail), (processingStep, pair) -> {
                if (processingStep.test()) {
                    return executeProcessingStep(processingStep, pair);
                }
                return processingStep;
            }, (a, b) -> {
                throw new NotImplementedException("Fold left implementation. Should never be called.");
            });

        lastStep.ghostInFlight(nonGhostedTerminalMail -> {
            if (!(Mail.ERROR.equals(mail.getState()))) {
                // Don't complain if we fall off the end of the error processor. That is currently the
                // normal situation for James, and the message will show up in the error store.
                LOGGER.warn("Message {} reached the end of this processor, and is automatically deleted. " +
                    "This may indicate a configuration error.", mail.getName());
                // Set the mail to ghost state
                mail.setState(Mail.GHOST);
            }
        });
        // The matcher splits creates intermediate emails, we need
        // to be sure to release allocated resources
        // Non ghosted emails emails are handled by other processors
        lastStep.disposeGhostedEncounteredMails();
    }

    private ProcessingStep executeProcessingStep(ProcessingStep step, Map.Entry<MatcherSplitter, ProcessorImpl> pair) {
        MatcherSplitter matcherSplitter = pair.getKey();
        ProcessorImpl processor = pair.getValue();
        ImmutableList<Mail> afterMatching = step.getInFlightMails()
            .stream()
            .flatMap(Throwing.<Mail, Stream<Mail>>function(mail -> matcherSplitter.split(mail).stream()).sneakyThrow())
            .collect(ImmutableList.toImmutableList());
        afterMatching
            .stream().filter(mail -> mail.removeAttribute(MATCHER_MATCHED_ATTRIBUTE).isPresent())
            .forEach(Throwing.consumer(processor::process).sneakyThrow());

        afterMatching.stream()
            .filter(mail -> !mail.getState().equals(getState()))
            .filter(mail -> !mail.getState().equals(Mail.GHOST))
            .forEach(Throwing.consumer(this::toProcessor).sneakyThrow());

        return step.nextStepBuilder()
            .inFlight(afterMatching.stream()
                .filter(mail -> mail.getState().equals(getState()))
                .collect(ImmutableList.toImmutableList()))
            .encountered(afterMatching);
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
        super.init();
    }

    @Override
    protected void setupRouting(List<MatcherMailetPair> pairs) throws MessagingException {
        try {
            this.pairs = pairs;
            this.pairsToBeProcessed = pairs.stream()
                .map(pair -> Pair.of(new MatcherSplitter(metricFactory, this, pair),
                    new ProcessorImpl(metricFactory, this, pair.getMailet())))
                .collect(ImmutableMap.toImmutableMap(Pair::getKey, Pair::getValue));
        } catch (Exception e) {
            throw new MessagingException("Unable to setup routing for MailetMatcherPairs", e);
        }
    }

}
