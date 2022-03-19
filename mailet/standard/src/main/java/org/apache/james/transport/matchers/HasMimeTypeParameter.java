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


package org.apache.james.transport.matchers;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import jakarta.activation.MimeType;
import jakarta.activation.MimeTypeParseException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.core.MailAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;
import org.apache.mailet.base.GenericMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;


/**
 * <p>This matcher checks if the content type parameters matches.</p>
 *
 * use: <pre>
 *     <code>
 *         <mailet match="HasMimeTypeParameter=report-type=disposition-notification,report-type=other" class="..." />
 *     </code>
 * </pre>
 */
public class HasMimeTypeParameter extends GenericMatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(HasMimeTypeParameter.class);

    @VisibleForTesting List<Pair<String, String>> filteredMimeTypeParameters;

    @Override
    public void init() throws MessagingException {
        filteredMimeTypeParameters = parseConfigurationString(Optional.ofNullable(getCondition()));
    }

    private List<Pair<String, String>> parseConfigurationString(Optional<String> maybeConfiguration) {
        return maybeConfiguration
            .map(tokenizeConfiguration())
            .orElse(ImmutableList.of());
    }

    private Function<String, List<Pair<String, String>>> tokenizeConfiguration() {
        return Throwing.<String, List<Pair<String, String>>>function(
            value -> {
                try {
                    return Splitter.on(",")
                        .trimResults()
                        .splitToStream(value)
                        .map(this::conditionToPair)
                        .collect(ImmutableList.toImmutableList());
                } catch (IllegalArgumentException e) {
                    throw new MailetException("error parsing configuration", e);
                }
            }
        ).sneakyThrow();
    }

    private Pair<String, String> conditionToPair(String s) {
        Preconditions.checkArgument(s.contains("="));
        List<String> pairElements = Splitter.on("=")
            .limit(2)
            .splitToList(s);
        validateCondition(pairElements);

        return Pair.of(pairElements.get(0), unQuote(pairElements.get(1)));
    }

    private void validateCondition(List<String> pairElements) {
        Preconditions.checkArgument(pairElements.stream().noneMatch(Strings::isNullOrEmpty),
            "Empty name or value for the parameter argument");
    }

    private String unQuote(String value) {
        return StringUtils.unwrap(value, '"');
    }

    @Override
    public Collection<MailAddress> match(Mail mail) throws MessagingException {
        Optional<MimeType> maybeMimeType = getMimeTypeFromMessage(mail.getMessage());
        if (maybeMimeType.map(this::mimeTypeMatchParameter).orElse(false)) {
            return mail.getRecipients();
        }
        return ImmutableList.of();
    }

    private Optional<MimeType> getMimeTypeFromMessage(MimeMessage message) throws MessagingException {
        try {
            return Optional.of(new MimeType(message.getContentType()));
        } catch (MimeTypeParseException e) {
            LOGGER.warn("Error while parsing message's mimeType {}", message.getContentType(), e);
            return Optional.empty();
        }
    }

    private boolean mimeTypeMatchParameter(MimeType mimeType) {
        return filteredMimeTypeParameters
            .stream()
            .anyMatch(entry -> mimeTypeContainsParameter(mimeType, entry.getKey(), entry.getValue()));
    }

    private boolean mimeTypeContainsParameter(MimeType mimeType, String name, String value) {
        return Optional.ofNullable(mimeType.getParameter(name)).map(value::equals).orElse(false);
    }

}

