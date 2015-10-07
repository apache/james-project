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

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.encode.base.AbstractChainedImapEncoder;
import org.apache.james.imap.message.response.SearchResponse;

/**
 * Encoders IMAP4rev1 <code>SEARCH</code> responses.
 */
public class SearchResponseEncoder extends AbstractChainedImapEncoder {

    public SearchResponseEncoder(ImapEncoder next) {
        super(next);
    }

    protected void doEncode(ImapMessage acceptableMessage, ImapResponseComposer composer, ImapSession session) throws IOException {
        SearchResponse response = (SearchResponse) acceptableMessage;
        final long[] ids = response.getIds();
        Long highestModSeq = response.getHighestModSeq();
        composer.untagged();
        composer.message(ImapConstants.SEARCH_RESPONSE_NAME);
        if (ids != null) {
            final int length = ids.length;
            for (int i = 0; i < length; i++) {
                final long id = ids[i];
                composer.message(id);
            }
        }
        
        // add MODSEQ
        if (highestModSeq != null) {
        	composer.openParen();
        	composer.message("MODSEQ");
        	composer.message(highestModSeq);
        	composer.closeParen();
        }
        composer.end();
    }

    protected boolean isAcceptable(ImapMessage message) {
        return (message instanceof SearchResponse);
    }
}
