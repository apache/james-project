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

package org.apache.james.rrt.lib;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.IntStream;

import jakarta.mail.internet.AddressException;

import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

@FunctionalInterface
public interface UserRewritter extends Serializable {

    Optional<Username> rewrite(Username username) throws AddressException, RecipientRewriteTable.ErrorMappingException;

    interface MappingUserRewriter {
        UserRewritter generateUserRewriter(String mapping);
    }

    class DomainRewriter implements MappingUserRewriter {
        @Override
        public UserRewritter generateUserRewriter(String mapping) {
            Domain newDomain = Domain.of(mapping);
            return oldUser -> Optional.of(
                Username.fromLocalPartWithDomain(
                    oldUser.getLocalPart(),
                    newDomain));
        }
    }

    class ReplaceRewriter implements MappingUserRewriter {
        @Override
        public UserRewritter generateUserRewriter(String mapping) {
            return oldUser -> Optional.of(Username.of(mapping));
        }
    }

    class ThrowingRewriter implements MappingUserRewriter {
        @Override
        public UserRewritter generateUserRewriter(String mapping) {
            return user -> {
                throw new RecipientRewriteTable.ErrorMappingException(mapping);
            };
        }
    }

    class RegexRewriter implements MappingUserRewriter {
        private static final Logger LOGGER = LoggerFactory.getLogger(RegexRewriter.class);

        private static final int REGEX = 0;
        private static final int PARAMETERIZED_STRING = 1;

        @Override
        public UserRewritter generateUserRewriter(String mapping) {
            return oldUser -> {
                try {
                    return regexMap(oldUser.asMailAddress(), mapping)
                        .map(Username::of);
                } catch (PatternSyntaxException e) {
                    LOGGER.error("Exception during regexMap processing: ", e);
                    return Optional.empty();
                }
            };
        }

        /**
         * Processes regex virtual user mapping
         *
         * It must be formatted as <regular-expression>:<parameterized-string>, e.g.,
         * (.*)@(.*):${1}@tld
         */
        public Optional<String> regexMap(MailAddress address, String mapping) {
            List<String> parts = ImmutableList.copyOf(Splitter.on(':').split(mapping));
            if (parts.size() != 2) {
                throw new PatternSyntaxException("Regex should be formatted as <regular-expression>:<parameterized-string>", mapping, 0);
            }

            Pattern pattern = Pattern.compile(parts.get(REGEX));
            Matcher match = pattern.matcher(address.asString());

            if (match.matches()) {
                ImmutableList<String> parameters = listMatchingGroups(match);
                return Optional.of(replaceParameters(parts.get(PARAMETERIZED_STRING), parameters));
            }
            return Optional.empty();
        }

        private ImmutableList<String> listMatchingGroups(Matcher match) {
            return IntStream
                .rangeClosed(1, match.groupCount())
                .mapToObj(match::group)
                .collect(ImmutableList.toImmutableList());
        }

        private String replaceParameters(String input, List<String> parameters) {
            int i = 1;
            for (String parameter: parameters) {
                input = input.replace("${" + i++ + "}", parameter);
            }
            return input;
        }
    }
}
