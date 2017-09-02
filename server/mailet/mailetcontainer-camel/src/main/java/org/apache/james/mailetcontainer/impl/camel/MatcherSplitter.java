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

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import javax.mail.MessagingException;

import org.apache.camel.Body;
import org.apache.camel.ExchangeProperty;
import org.apache.camel.Handler;
import org.apache.camel.InOnly;
import org.apache.james.core.MailImpl;
import org.apache.james.mailetcontainer.impl.ProcessorUtil;
import org.apache.james.mailetcontainer.lib.AbstractStateMailetProcessor.MailetProcessorListener;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.api.TimeMetric;
import org.apache.james.util.MDCBuilder;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.Matcher;
import org.slf4j.Logger;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

/**
 * A Splitter for use with Camel to split the MailMessage into many pieces if
 * needed. This is done by use a Matcher.
 */
@InOnly
public class MatcherSplitter {

    /** Headername which is used to indicate that the matcher matched */
    public final static String MATCHER_MATCHED_ATTRIBUTE = "matched";

    /** Headername under which the matcher is stored */
    public final static String MATCHER_PROPERTY = "matcher";

    public final static String ON_MATCH_EXCEPTION_PROPERTY = "onMatchException";

    public final static String LOGGER_PROPERTY = "logger";

    public final static String MAILETCONTAINER_PROPERTY = "container";

    public final static String METRIC_FACTORY = "metricFactory";

    /**
     * Generate a List of MailMessage instances for the give @Body. This is done
     * by using the given Matcher to see if we need more then one instance of
     * the MailMessage
     * 
     * @param matcher
     *            Matcher to use for splitting
     * @param mail
     *            Mail which is stored in the @Body of the MailMessage
     * @return mailMessageList
     * @throws MessagingException
     */
    @Handler
    public List<Mail> split(@ExchangeProperty(MATCHER_PROPERTY) Matcher matcher,
                            @ExchangeProperty(ON_MATCH_EXCEPTION_PROPERTY) String onMatchException,
                            @ExchangeProperty(LOGGER_PROPERTY) Logger logger,
                            @ExchangeProperty(MAILETCONTAINER_PROPERTY) CamelMailetProcessor container,
                            @ExchangeProperty(METRIC_FACTORY) MetricFactory metricFactory,
                            @Body Mail mail) throws MessagingException {
        Collection<MailAddress> matchedRcpts = null;
        Collection<MailAddress> origRcpts = new ArrayList<>(mail.getRecipients());
        long start = System.currentTimeMillis();
        MessagingException ex = null;
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
                         .addContext("sender", mail.getSender())
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

            } catch (MessagingException me) {
                ex = me;
                if (onMatchException == null) {
                    onMatchException = Mail.ERROR;
                } else {
                    onMatchException = onMatchException.trim().toLowerCase(Locale.US);
                }
                if (onMatchException.compareTo("nomatch") == 0) {
                    // In case the matcher returned null, create an empty
                    // Collection
                    matchedRcpts = new ArrayList<>(0);
                } else if (onMatchException.compareTo("matchall") == 0) {
                    matchedRcpts = mail.getRecipients();
                    // no need to verify addresses
                } else {
                    ProcessorUtil.handleException(me, mail, matcher.getMatcherConfig().getMatcherName(), onMatchException, logger);
                }
            } catch (IOException e) {
                throw Throwables.propagate(e);
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

                    Mail newMail = new MailImpl(mail);
                    newMail.setRecipients(matchedRcpts);

                    // Set a header because the matcher matched. This can be
                    // used later when processing the route
                    newMail.setAttribute(MATCHER_MATCHED_ATTRIBUTE, true);

                    // add the new generated mail to the mails list
                    mails.add(newMail);
                }
            }

            if (fullMatch) {
                // Set a header because the matcher matched. This can be used
                // later when processing the route
                mail.setAttribute(MATCHER_MATCHED_ATTRIBUTE, true);
            }

            // add mailMsg to the mails list
            mails.add(mail);

            return mails;
        } finally {
            timeMetric.stopAndPublish();
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
