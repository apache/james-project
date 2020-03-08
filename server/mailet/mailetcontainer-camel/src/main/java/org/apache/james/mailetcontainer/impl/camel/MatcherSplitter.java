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

import static org.apache.james.metrics.api.TimeMetric.ExecutionResult.DEFAULT_100_MS_THRESHOLD;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.mail.MessagingException;

import org.apache.camel.Body;
import org.apache.camel.Handler;
import org.apache.camel.InOnly;
import org.apache.james.core.MailAddress;
import org.apache.james.mailetcontainer.impl.MatcherMailetPair;
import org.apache.james.mailetcontainer.impl.ProcessorUtil;
import org.apache.james.mailetcontainer.lib.AbstractStateMailetProcessor.MailetProcessorListener;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.server.core.MailImpl;
import org.apache.james.util.MDCBuilder;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

/**
 * A Splitter for use with Camel to split the MailMessage into many pieces if
 * needed. This is done by use a Matcher.
 */
@InOnly
public class MatcherSplitter {
    private static final Logger LOGGER = LoggerFactory.getLogger(MatcherSplitter.class);

    /** Headername which is used to indicate that the matcher matched */
    public static final AttributeName MATCHER_MATCHED_ATTRIBUTE = AttributeName.of("matched");

    private final MetricFactory metricFactory;
    private final CamelMailetProcessor container;
    private final Matcher matcher;
    private final String onMatchException;

    public MatcherSplitter(MetricFactory metricFactory, CamelMailetProcessor container, MatcherMailetPair pair) {
        this.metricFactory = metricFactory;
        this.container = container;
        this.matcher = pair.getMatcher();
        this.onMatchException = Optional.ofNullable(pair.getOnMatchException())
            .map(s -> s.trim().toLowerCase(Locale.US))
            .orElse(Mail.ERROR);
    }

    /**
     * Generate a List of MailMessage instances for the give @Body. This is done
     * by using the given Matcher to see if we need more then one instance of
     * the MailMessage
     *
     * @param mail
     *            Mail which is stored in the @Body of the MailMessage
     * @return mailMessageList
     */
    @Handler
    public List<Mail> split(@Body Mail mail) throws MessagingException {
        Collection<MailAddress> matchedRcpts = null;
        Collection<MailAddress> origRcpts = new ArrayList<>(mail.getRecipients());
        long start = System.currentTimeMillis();
        Exception ex = null;
        TimeMetric timeMetric = metricFactory.timer(matcher.getClass().getSimpleName());

        try {
            List<Mail> mails = new ArrayList<>();
            boolean fullMatch = false;

            try (Closeable closeable =
                     MDCBuilder.create()
                         .addContext(MDCBuilder.PROTOCOL, "MAILET")
                         .addContext(MDCBuilder.ACTION, "MATCHER")
                         .addContext(MDCBuilder.IP, mail.getRemoteAddr())
                         .addContext(MDCBuilder.HOST, mail.getRemoteHost())
                         .addContext("matcher", matcher.getMatcherInfo())
                         .addContext("state", mail.getState())
                         .addContext("mail", mail.getName())
                         .addContext("recipients", ImmutableList.copyOf(mail.getRecipients()))
                         .addContext("sender", mail.getMaybeSender())
                         .build()) {
                // call the matcher
                matchedRcpts = matcher.match(mail);

                if (matchedRcpts == null) {
                    // In case the matcher returned null, create an empty
                    // Collection
                    matchedRcpts = new ArrayList<>(0);
                } else if (matchedRcpts != mail.getRecipients()) {
                    // Make sure all the objects are MailAddress objects
                    ProcessorUtil.verifyMailAddresses(matchedRcpts);
                }

            } catch (Exception me) {
                ex = me;
                if (onMatchException.equalsIgnoreCase("nomatch")) {
                    // In case the matcher returned null, create an empty
                    // Collection
                    LOGGER.warn("Encountered error while executing matcher {}. Matching none.", matcher, ex);
                    matchedRcpts = new ArrayList<>(0);
                } else if (onMatchException.equalsIgnoreCase("matchall")) {
                    LOGGER.warn("Encountered error while executing matcher {}. matching all.", matcher, ex);
                    matchedRcpts = mail.getRecipients();
                    // no need to verify addresses
                } else {
                    ProcessorUtil.handleException(me, mail, matcher.getMatcherConfig().getMatcherName(), onMatchException, LOGGER);
                }
            }

            // check if the matcher matched
            if (matchedRcpts != null && !matchedRcpts.isEmpty()) {
                List<MailAddress> rcpts = new ArrayList<>(mail.getRecipients());

                for (MailAddress matchedRcpt : matchedRcpts) {
                    // loop through the recipients and remove the recipients
                    // that matched
                    rcpts.remove(matchedRcpt);
                }

                if (rcpts.isEmpty()) {
                    // all recipients matched
                    fullMatch = true;
                } else {
                    mail.setRecipients(rcpts);

                    Mail newMail = MailImpl.duplicate(mail);
                    newMail.setRecipients(matchedRcpts);

                    // Set a header because the matcher matched. This can be
                    // used later when processing the route
                    newMail.setAttribute(new Attribute(MATCHER_MATCHED_ATTRIBUTE, AttributeValue.of(true)));

                    // add the new generated mail to the mails list
                    mails.add(newMail);
                }
            }

            if (fullMatch) {
                // Set a header because the matcher matched. This can be used
                // later when processing the route
                mail.setAttribute(new Attribute(MATCHER_MATCHED_ATTRIBUTE, AttributeValue.of(true)));
            }

            // add mailMsg to the mails list
            mails.add(mail);

            return mails;
        } finally {
            timeMetric.stopAndPublish().logWhenExceedP99(DEFAULT_100_MS_THRESHOLD);
            long complete = System.currentTimeMillis() - start;
            List<MailetProcessorListener> listeners = container.getListeners();
            for (MailetProcessorListener listener : listeners) {
                // need to check if its null or empty!
                if (matchedRcpts == null || matchedRcpts.isEmpty()) {
                    listener.afterMatcher(matcher, mail.getName(), origRcpts, null, complete, ex);
                } else {
                    listener.afterMatcher(matcher, mail.getName(), origRcpts, matchedRcpts, complete, ex);
                }
            }
        }
    }
}
