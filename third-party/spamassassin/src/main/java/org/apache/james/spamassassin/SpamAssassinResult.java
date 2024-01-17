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
package org.apache.james.spamassassin;

import java.util.List;

import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class SpamAssassinResult {
    /** The mail attribute under which the status get stored */
    public static final AttributeName STATUS_MAIL = AttributeName.of("org.apache.james.spamassassin.status");

    /** The mail attribute under which the flag get stored */
    public static final AttributeName FLAG_MAIL = AttributeName.of("org.apache.james.spamassassin.flag");

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

            ImmutableList.Builder<Attribute> headersAsAttributes = ImmutableList.builder();
            if (isSpam) {
                headersAsAttributes.add(new Attribute(FLAG_MAIL, AttributeValue.of("YES")));
                headersAsAttributes.add(new Attribute(STATUS_MAIL, AttributeValue.of("Yes, hits=" + hits + " required=" + requiredHits)));
            } else {
                headersAsAttributes.add(new Attribute(FLAG_MAIL, AttributeValue.of("NO")));
                headersAsAttributes.add(new Attribute(STATUS_MAIL, AttributeValue.of("No, hits=" + hits + " required=" + requiredHits)));
            }

            SpamStatus status = isSpam ? SpamStatus.Spam : SpamStatus.Ham;

            return new SpamAssassinResult(status, hits, requiredHits, headersAsAttributes.build());
        }
    }

    enum SpamStatus {
        Spam,
        Ham
    }

    private final SpamStatus status;
    private final String hits;
    private final String requiredHits;
    private final List<Attribute> headersAsAttributes;

    private SpamAssassinResult(SpamStatus status, String hits, String requiredHits, List<Attribute> headersAsAttributes) {
        this.status = status;
        this.hits = hits;
        this.requiredHits = requiredHits;
        this.headersAsAttributes = headersAsAttributes;
    }

    public String getHits() {
        return hits;
    }

    public String getRequiredHits() {
        return requiredHits;
    }

    public SpamStatus getStatus() {
        return status;
    }

    public List<Attribute> getHeadersAsAttributes() {
        return headersAsAttributes;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("status", status)
            .add("hits", hits)
            .add("requiredHits", requiredHits)
            .add("headersAsAttributes", headersAsAttributes)
            .toString();
    }
}
