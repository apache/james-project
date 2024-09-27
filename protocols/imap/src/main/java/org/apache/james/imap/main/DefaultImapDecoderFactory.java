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

package org.apache.james.imap.main;

import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.decode.ImapCommandParserFactory;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.decode.ImapDecoderFactory;
import org.apache.james.imap.decode.main.DefaultImapDecoder;
import org.apache.james.imap.decode.parser.ImapParserFactory;
import org.apache.james.imap.message.response.UnpooledStatusResponseFactory;

/**
 * Factory class for creating `ImapDecoder` instances.
 * <p>
 * This class is a POJO that manually manages its dependencies.
 * Dependencies are injected through the constructor, which allows for
 * better decoupling and easier testing.
 * <p>
 * The creation of `ImapCommandParserFactory` is handled internally by
 * this factory, based on the provided `UnpooledStatusResponseFactory`.
 */
public class DefaultImapDecoderFactory implements ImapDecoderFactory {

    private final StatusResponseFactory statusResponseFactory;
    private final ImapCommandParserFactory imapCommandParserFactory;

    public DefaultImapDecoderFactory() {
        this(new UnpooledStatusResponseFactory());
    }

    public DefaultImapDecoderFactory(StatusResponseFactory statusResponseFactory) {
        this.statusResponseFactory = statusResponseFactory;
        this.imapCommandParserFactory = new ImapParserFactory(statusResponseFactory);
    }

    public DefaultImapDecoderFactory(ImapCommandParserFactory imapCommandParserFactory, StatusResponseFactory statusResponseFactory) {
        this.statusResponseFactory = statusResponseFactory;
        this.imapCommandParserFactory = imapCommandParserFactory;
    }

    @Override
    public ImapDecoder buildImapDecoder() {
        return new DefaultImapDecoder(statusResponseFactory, imapCommandParserFactory);
    }
}
