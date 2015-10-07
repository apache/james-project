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

package org.apache.james.imap.encode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.display.Locales;
import org.apache.james.imap.api.display.Localizer;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.encode.base.AbstractChainedImapEncoder;
import org.apache.james.imap.message.response.ContinuationResponse;

public class ContinuationResponseEncoder extends AbstractChainedImapEncoder {

    private final Localizer localizer;

    public ContinuationResponseEncoder(ImapEncoder next, final Localizer localizer) {
        super(next);
        this.localizer = localizer;
    }

    protected void doEncode(ImapMessage acceptableMessage, ImapResponseComposer composer, ImapSession session) throws IOException {

        ContinuationResponse response = (ContinuationResponse) acceptableMessage;
        final String message = response.getData() != null ? response.getData() : asString(response.getTextKey(), session);
        composer.continuationResponse(message);
    }

    private String asString(HumanReadableText text, ImapSession session) {
        // TODO: calculate locales
        return localizer.localize(text, new Locales(new ArrayList<Locale>(), null));
    }

    protected boolean isAcceptable(ImapMessage message) {
        return (message instanceof ContinuationResponse);
    }

}
