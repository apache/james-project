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
import java.util.List;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.Tag;
import org.apache.james.imap.api.message.IdRange;
import org.apache.james.imap.api.message.request.SearchResultOption;
import org.apache.james.imap.message.response.ESearchResponse;
import org.apache.james.mailbox.ModSeq;

import com.github.fge.lambdas.Throwing;

/**
 * Encoders IMAP4rev1 <code>ESEARCH</code> responses.
 */
public class ESearchResponseEncoder implements ImapResponseEncoder<ESearchResponse> {
    @Override
    public Class<ESearchResponse> acceptableMessages() {
        return ESearchResponse.class;
    }

    @Override
    public void encode(ESearchResponse response, ImapResponseComposer composer) throws IOException {
        Tag tag = response.getTag();
        long min = response.getMinUid();
        long max = response.getMaxUid();
        long count = response.getCount();
        IdRange[] all = response.getAll();
        IdRange[] partialUids = response.getPartialUids();
        boolean useUid = response.getUseUid();
        ModSeq highestModSeq = response.getHighestModSeq();
        List<SearchResultOption> options = response.getSearchResultOptions();
        
        composer.untagged().message("ESEARCH").openParen().message("TAG").quote(tag.asString()).closeParen();
        if (useUid) {
            composer.message(ImapConstants.UID);
        }
        if (min > -1 && options.contains(SearchResultOption.MIN)) {
            composer.message(SearchResultOption.MIN.name()).message(min);
        }
        if (max > -1 && options.contains(SearchResultOption.MAX)) {
            composer.message(SearchResultOption.MAX.name()).message(max);
        }
        if (options.contains(SearchResultOption.COUNT)) {
            composer.message(SearchResultOption.COUNT.name()).message(count);
        }
        if (options.contains(SearchResultOption.PARTIAL)) {
            composer.message(SearchResultOption.PARTIAL.name());
            composer.openParen();
            response.getPartialRange()
                .ifPresent(Throwing.consumer(partialRange ->
                    composer.message(partialRange.getLowVal() + ":" + partialRange.getUpVal())));
            composer.sequenceSet(partialUids);
            composer.closeParen();

        }
        if (all != null && all.length > 0 && options.contains(SearchResultOption.ALL)) {
            composer.message(SearchResultOption.ALL.name());
            composer.sequenceSet(all);
        }
        
        // Add the MODSEQ to the response if needed. 
        //
        // see RFC4731 3.2.  Interaction with CONDSTORE extension
        if (highestModSeq != null) {
            composer.message(ImapConstants.FETCH_MODSEQ);
            composer.message(highestModSeq.asLong());
        }
        composer.end();
    }
}
