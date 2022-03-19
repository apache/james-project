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

package org.apache.james.jdkim.mailets;

import java.util.List;
import java.util.Locale;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.jdkim.api.Headers;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Streams;

/**
 * An adapter to let DKIMSigner read headers from MimeMessage
 */
final class MimeMessageHeaders implements Headers {

    private final ImmutableListMultimap<String, String> headers;
    private final List<String> fields;

    public MimeMessageHeaders(MimeMessage message) throws MessagingException {
        ImmutableList<Pair<String, String>> headsAndLines = Streams.stream(Iterators.forEnumeration(message.getAllHeaderLines()))
                .map(Throwing.function(this::extractHeaderLine).sneakyThrow())
                .collect(ImmutableList.toImmutableList());

        fields = headsAndLines
            .stream()
            .map(Pair::getKey)
            .collect(ImmutableList.toImmutableList());

        headers = headsAndLines
            .stream()
            .collect(ImmutableListMultimap.toImmutableListMultimap(
                pair -> pair.getKey().toLowerCase(Locale.US),
                Pair::getValue));
    }

    public List<String> getFields() {
        return fields;
    }

    public List<String> getFields(String name) {
        return headers.get(name.toLowerCase(Locale.US));
    }

    private Pair<String, String> extractHeaderLine(String header) throws MessagingException {
        int fieldSeperatorPosition = header.indexOf(':');
        if (fieldSeperatorPosition <= 0) {
            throw new MessagingException("Bad header line: " + header);
        }
        return Pair.of(header.substring(0, fieldSeperatorPosition).trim(), header);
    }
}