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

package org.apache.james.imap;

import org.apache.james.imap.api.process.ImapProcessor;
import org.apache.james.imap.decode.ImapDecoder;
import org.apache.james.imap.encode.ImapEncoder;

public class ImapSuite {
    private final ImapDecoder decoder;
    private final ImapEncoder encoder;
    private final ImapProcessor processor;

    public ImapSuite(ImapDecoder decoder, ImapEncoder encoder, ImapProcessor processor) {
        this.decoder = decoder;
        this.encoder = encoder;
        this.processor = processor;
    }

    public ImapDecoder getDecoder() {
        return decoder;
    }

    public ImapEncoder getEncoder() {
        return encoder;
    }

    public ImapProcessor getProcessor() {
        return processor;
    }
}
