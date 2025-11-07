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

package org.apache.james.transport.mailets;

import java.util.List;
import java.util.Set;

import jakarta.mail.Header;
import jakarta.mail.MessagingException;

import org.apache.commons.lang3.stream.Streams;
import org.apache.james.mime4j.util.MimeUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;

/**
 * This mailet fold (wrap) any header lines of the mail that exceed the maximum number of characters.
 * <br />
 * It takes only one parameter:
 * <ul>
 * <li>maxCharacters: maximum number of characters. Default to 998.
 * </ul>
 */
public class FoldLongLines extends GenericMailet {
    public static final String MAX_CHARACTERS_PARAMETER_NAME = "maxCharacters";
    public static final String HEADER_SEPARATOR = ": ";

    private static final int DEFAULT_MAX_CHARACTERS = 998;
    private static final String SEPARATOR = "\n";

    private int maxCharacters;

    @Override
    public void init() throws MessagingException {
        int maxCharacters = getInitParameterAsOptional(MAX_CHARACTERS_PARAMETER_NAME).map(Integer::parseInt).orElse(DEFAULT_MAX_CHARACTERS);
        Preconditions.checkArgument(maxCharacters > 0, "maxCharacters must be positive");
        this.maxCharacters = maxCharacters;
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        Set<String> longHeaders = getHeadersExceedingMaxCharacters(mail);

        if (!longHeaders.isEmpty()) {
            List<Header> headers = getHeadersWithTheSameNameAsLongHeaders(mail, longHeaders);

            // remove all long headers (as well as headers with same name)
            longHeaders.forEach(Throwing.consumer(header -> mail.getMessage().removeHeader(header)));

            headers.forEach(Throwing.consumer(header -> {
                if (exceedLineLimit(header)) {
                    mail.getMessage().addHeader(header.getName(), fold(header));
                } else {
                    mail.getMessage().addHeader(header.getName(), header.getValue());
                }
            }));
            mail.getMessage().saveChanges();
        }
    }

    private Set<String> getHeadersExceedingMaxCharacters(Mail mail) throws MessagingException {
        return Streams.of(mail.getMessage().getAllHeaders().asIterator())
            .filter(this::exceedLineLimit)
            .map(Header::getName)
            .collect(ImmutableSet.toImmutableSet());
    }

    private List<Header> getHeadersWithTheSameNameAsLongHeaders(Mail mail, Set<String> longHeaders) throws MessagingException {
        return Streams.of(mail.getMessage().getAllHeaders().asIterator())
            .filter(header -> longHeaders.contains(header.getName()))
            .toList();
    }

    private String fold(Header header) {
        int headerNameLength = header.getName().length() + HEADER_SEPARATOR.length();
        // TODO After new release of mime4j with commit https://github.com/apache/james-mime4j/commit/66a09219457854c7a26e5b7c0e4c9dd59b4b0c32, update to use MimeUtil of mime4j and remove MimeUtil class file
        return MimeUtil.fold(header.getValue(), headerNameLength, maxCharacters);
    }

    private boolean exceedLineLimit(Header header) {
        String fullHeader = header.getName() + HEADER_SEPARATOR + header.getValue();
        return Splitter.on(SEPARATOR)
            .splitToStream(fullHeader)
            .anyMatch(line -> line.length() > maxCharacters);
    }
}