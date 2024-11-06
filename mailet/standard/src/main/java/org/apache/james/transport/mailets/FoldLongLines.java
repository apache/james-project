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

import jakarta.mail.Header;
import jakarta.mail.MessagingException;

import org.apache.commons.lang3.stream.Streams;
import org.apache.mailet.Mail;
import org.apache.mailet.base.GenericMailet;

import com.github.fge.lambdas.Throwing;

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

    private static final int DEFAULT_MAX_CHARACTERS = 998;

    private Integer maxCharacters;

    @Override
    public void init() throws MessagingException {
        maxCharacters = getInitParameterAsOptional(MAX_CHARACTERS_PARAMETER_NAME).map(Integer::parseInt).orElse(DEFAULT_MAX_CHARACTERS);
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        List<Header> foldedHeaders = Streams.of(mail.getMessage().getAllHeaders().asIterator()).map(this::fold).toList();
        foldedHeaders.forEach(Throwing.consumer(header -> mail.getMessage().setHeader(header.getName(), header.getValue())));
    }

    private Header fold(Header header) {
        // TODO After new release of mime4j, update to use MimeUtil of mime4j and remove MimeUtil class file
        String folded = MimeUtil.fold(header.getValue(), header.getName().length() + 2, maxCharacters);
        return new Header(header.getName(), folded);
    }
}