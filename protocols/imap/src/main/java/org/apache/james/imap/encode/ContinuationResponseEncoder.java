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

import jakarta.inject.Inject;

import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.display.Locales;
import org.apache.james.imap.api.display.Localizer;
import org.apache.james.imap.message.response.ContinuationResponse;

public class ContinuationResponseEncoder implements ImapResponseEncoder<ContinuationResponse> {

    private final Localizer localizer;

    @Inject
    public ContinuationResponseEncoder(Localizer localizer) {
        this.localizer = localizer;
    }

    @Override
    public Class<ContinuationResponse> acceptableMessages() {
        return ContinuationResponse.class;
    }

    @Override
    public void encode(ContinuationResponse response, ImapResponseComposer composer) throws IOException {
        String message = asString(response.getTextKey());
        composer.continuationResponse(message);
    }

    private String asString(HumanReadableText text) {
        // TODO: calculate locales
        return localizer.localize(text, Locales.DEFAULT);
    }
}
