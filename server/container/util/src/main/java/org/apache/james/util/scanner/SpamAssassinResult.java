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
package org.apache.james.util.scanner;

import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

public class SpamAssassinResult {
    /** The mail attribute under which the status get stored */
    public static final String STATUS_MAIL_ATTRIBUTE_NAME = "org.apache.james.spamassassin.status";

    /** The mail attribute under which the flag get stored */
    public static final String FLAG_MAIL_ATTRIBUTE_NAME = "org.apache.james.spamassassin.flag";

    public static final String NO_RESULT = "?";

    public static SpamAssassinResult empty() {
        return asHam()
                .hits(NO_RESULT)
                .requiredHits(NO_RESULT)
                .build();
    }

    public static Builder asSpam() {
        return new Builder(true);
    }

    public static Builder asHam() {
        return new Builder(false);
    }

    public static class Builder {
        
        private String hits;
        private String requiredHits;
        private final boolean isSpam;

        private Builder(boolean isSpam) {
            this.isSpam = isSpam;
        }

        public Builder hits(String hits) {
            this.hits = hits;
            return this;
        }

        public Builder requiredHits(String requiredHits) {
            this.requiredHits = requiredHits;
            return this;
        }

        public SpamAssassinResult build() {
            Preconditions.checkNotNull(hits);
            Preconditions.checkNotNull(requiredHits);

            ImmutableMap.Builder<String, String> headersAsAttribute = ImmutableMap.builder();
            if (isSpam) {
                headersAsAttribute.put(FLAG_MAIL_ATTRIBUTE_NAME, "YES");
                headersAsAttribute.put(STATUS_MAIL_ATTRIBUTE_NAME, "Yes, hits=" + hits + " required=" + requiredHits);
            } else {
                headersAsAttribute.put(FLAG_MAIL_ATTRIBUTE_NAME, "NO");
                headersAsAttribute.put(STATUS_MAIL_ATTRIBUTE_NAME, "No, hits=" + hits + " required=" + requiredHits);
            }

            return new SpamAssassinResult(hits, requiredHits, headersAsAttribute.build());
        }
    }

    private final String hits;
    private final String requiredHits;
    private final Map<String, String> headersAsAttribute;

    private SpamAssassinResult(String hits, String requiredHits, Map<String, String> headersAsAttribute) {
        this.hits = hits;
        this.requiredHits = requiredHits;
        this.headersAsAttribute = headersAsAttribute;
    }

    public String getHits() {
        return hits;
    }

    public String getRequiredHits() {
        return requiredHits;
    }

    public Map<String, String> getHeadersAsAttribute() {
        return headersAsAttribute;
    }

}
