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

package org.apache.james.jmap.api.projections;

import java.util.Objects;

import org.apache.james.jmap.api.preview.Preview;

import com.google.common.base.Preconditions;

public class MessageFastViewPrecomputedProperties {
    public static class Builder {
        @FunctionalInterface
        public interface RequirePreview {
            FinalStage preview(Preview preview);
        }

        public static class FinalStage {
            private final Preview preview;

            private FinalStage(Preview preview) {
                Preconditions.checkNotNull(preview, "'preview' cannot be null");
                this.preview = preview;
            }

            public MessageFastViewPrecomputedProperties build() {
                return new MessageFastViewPrecomputedProperties(preview);
            }
        }
    }

    public static Builder.RequirePreview builder() {
        return preview -> new Builder.FinalStage(preview);
    }

    private final Preview preview;

    private MessageFastViewPrecomputedProperties(Preview preview) {
        this.preview = preview;
    }

    public Preview getPreview() {
        return preview;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof MessageFastViewPrecomputedProperties) {
            MessageFastViewPrecomputedProperties that = (MessageFastViewPrecomputedProperties) o;

            return Objects.equals(this.preview, that.preview);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(preview);
    }
}
