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

package org.apache.james.jmap.api.preview;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class Preview {

    private static final int MAX_LENGTH = 256;

    public static Preview from(String value) {
        return new Preview(value);
    }

    public static Preview compute(String textBody) {
        return Preview.from(
            truncateToMaxLength(
                StringUtils.normalizeSpace(textBody)));
    }

    private static String truncateToMaxLength(String body) {
        return StringUtils.left(body, MAX_LENGTH);
    }

    private final String value;

    @VisibleForTesting
    Preview(String value) {
        Preconditions.checkNotNull(value);
        Preconditions.checkArgument(value.length() <= MAX_LENGTH,
            String.format("the preview value '%s' has length longer than %d", value, MAX_LENGTH));

        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Preview) {
            Preview preview = (Preview) o;

            return Objects.equals(this.value, preview.value);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("value", value)
            .toString();
    }
}
