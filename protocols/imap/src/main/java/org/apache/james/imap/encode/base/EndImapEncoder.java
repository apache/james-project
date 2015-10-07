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

package org.apache.james.imap.encode.base;

import java.io.IOException;

import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.encode.ImapEncoder;
import org.apache.james.imap.encode.ImapResponseComposer;
import org.slf4j.Logger;

/**
 * {@link ImapEncoder} which should get added to the end of the encoder chain.
 * It will handle the response to the client about the unknown
 * {@link ImapMessage}
 */
public class EndImapEncoder implements ImapEncoder {

    /**
     * @see
     * org.apache.james.imap.encode.ImapEncoder#encode(org.apache.james.imap.api.ImapMessage, org.apache.james.imap.encode.ImapResponseComposer,
     * org.apache.james.imap.api.process.ImapSession)
     */
    public void encode(ImapMessage message, ImapResponseComposer composer, ImapSession session) throws IOException {
        final Logger logger = session.getLog();
        if (logger.isWarnEnabled()) {
            logger.warn("Unknown message " + message);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Chain end reached for " + message);
        }
        composer.untaggedNoResponse("Unknown message in pipeline", null);
    }
}
