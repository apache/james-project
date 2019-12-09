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
import java.util.Set;

import org.apache.james.imap.api.message.Capability;
import org.apache.james.imap.message.response.EnableResponse;

/**
 * Encodes <code>Enable</code> response.
 */
public class EnableResponseEncoder implements ImapResponseEncoder<EnableResponse> {
    @Override
    public Class<EnableResponse> acceptableMessages() {
        return EnableResponse.class;
    }

    @Override
    public void encode(EnableResponse response, ImapResponseComposer composer) throws IOException {
        Set<Capability> capabilities = response.getCapabilities();
        composer.untagged();
        // Return ENABLED capabilities. See IMAP-323
        composer.message("ENABLED");
        for (Capability capability : capabilities) {
            composer.message(capability.asString());
        }
        composer.end();
    }
}
