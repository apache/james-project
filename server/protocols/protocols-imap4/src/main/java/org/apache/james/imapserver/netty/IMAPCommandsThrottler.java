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

package org.apache.james.imapserver.netty;

import static org.apache.james.imapserver.netty.NettyConstants.IMAP_SESSION_ATTRIBUTE_KEY;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.ImmutableHierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.imap.api.message.request.ImapRequest;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.processor.IdProcessor;
import org.apache.james.util.DurationParser;
import org.apache.james.util.MDCStructuredLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import it.unimi.dsi.fastutil.Pair;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class IMAPCommandsThrottler extends ChannelInboundHandlerAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(IMAPCommandsThrottler.class);

    public record ThrottlerConfigurationEntry(
        int thresholdCount,
        Duration additionalDelayPerOperation,
        Duration observationPeriod,
        Duration maxDelay) {

        public static ThrottlerConfigurationEntry from(ImmutableHierarchicalConfiguration configuration) {
            return new ThrottlerConfigurationEntry(
                Optional.ofNullable(configuration.getString("thresholdCount", null))
                    .map(Integer::parseInt)
                    .orElseThrow(() -> new IllegalArgumentException("thresholdCount in compulsory for ThrottlerConfigurationEntry")),
                Optional.ofNullable(configuration.getString("additionalDelayPerOperation", null))
                    .map(DurationParser::parse)
                    .orElseThrow(() -> new IllegalArgumentException("additionalDelayPerOperation in compulsory for ThrottlerConfigurationEntry")),
                Optional.ofNullable(configuration.getString("observationPeriod", null))
                    .map(DurationParser::parse)
                    .orElseThrow(() -> new IllegalArgumentException("observationPeriod in compulsory for ThrottlerConfigurationEntry")),
                Optional.ofNullable(configuration.getString("maxDelay", null))
                    .map(DurationParser::parse)
                    .orElseThrow(() -> new IllegalArgumentException("maxDelay in compulsory for ThrottlerConfigurationEntry")));
        }

        long delayMSFor(long occurrenceCount) {
            if (occurrenceCount < thresholdCount) {
                return 0;
            }

            return Math.min(maxDelay.toMillis(), occurrenceCount * additionalDelayPerOperation.toMillis());
        }
    }

    public record ThrottlerConfiguration(Map<String, ThrottlerConfigurationEntry> entryMap) {
        public static ThrottlerConfiguration from(HierarchicalConfiguration<ImmutableNode> configuration) {
            return new ThrottlerConfiguration(configuration.getNodeModel()
                .getNodeHandler()
                .getRootNode()
                .getChildren()
                .stream()
                .map(key -> Pair.of(key.getNodeName().toUpperCase(Locale.US), ThrottlerConfigurationEntry.from(configuration.immutableConfigurationAt(key.getNodeName()))))
                .collect(ImmutableMap.toImmutableMap(Pair::key, Pair::value)));
        }
    }

    private final ThrottlerConfiguration configuration;

    public IMAPCommandsThrottler(ThrottlerConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ImapRequest imapRequest) {
            String key = imapRequest.getCommand().getName().toUpperCase(Locale.US);
            Optional.ofNullable(configuration.entryMap().get(key))
                .ifPresentOrElse(configurationEntry -> throttle(ctx, msg, imapRequest, configurationEntry),
                    () -> ctx.fireChannelRead(msg));
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    private static void throttle(ChannelHandlerContext ctx, Object msg, ImapRequest imapRequest, ThrottlerConfigurationEntry configurationEntry) {
        ImapSession session = (ImapSession) ctx.channel().attr(IMAP_SESSION_ATTRIBUTE_KEY);

        AtomicLong atomicLong = retrieveAssociatedCounter(imapRequest, session, configurationEntry);
        Duration delay = Duration.ofMillis(configurationEntry.delayMSFor(atomicLong.getAndIncrement()));

        if (delay.isPositive()) {
            logDelay(imapRequest, session, delay);

            Mono.delay(delay)
                .then(Mono.fromRunnable(() -> ctx.fireChannelRead(msg)))
                .subscribeOn(Schedulers.parallel())
                .subscribe();
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    private static AtomicLong retrieveAssociatedCounter(ImapRequest imapRequest, ImapSession session, ThrottlerConfigurationEntry entry) {
        String key = "imap-applicative-traffic-shaper-counter-" + imapRequest.getCommand().getName();
        return Optional.ofNullable(session.getAttribute(key))
            .filter(AtomicLong.class::isInstance)
            .map(AtomicLong.class::cast)
            .orElseGet(() -> {
                AtomicLong res = new AtomicLong(0);
                session.setAttribute(key, res);
                session.schedule(() -> session.setAttribute(key, new AtomicLong(0)), entry.observationPeriod());
                return res;
            });
    }

    private static void logDelay(ImapRequest imapRequest, ImapSession session, Duration delay) {
        MDCStructuredLogger.forLogger(LOGGER)
            .field("username", session.getUserName().asString())
            .field("userAgent", Optional.ofNullable(session.getAttribute(IdProcessor.USER_AGENT))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .orElse(""))
            .log(logger -> logger.info("Delayed command {} on an IMAP session. Delay {} ms", 
                imapRequest.getCommand().getName(),
                delay.toMillis()));
    }
}
