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

package org.apache.james.mailbox.model;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class ContentType {
    public static ContentType of(String value) {
        Preconditions.checkState(!Strings.isNullOrEmpty(value), "'content type' is mandatory");
        return new ContentType(value);
    }

    /**
     * Follows syntax and usage as defined in https://tools.ietf.org/html/rfc2045#section-5
     * Thus includes media type and parameters, including charset
     * Example: text/plain; charset=utf-8
     */
    private final String value;

    public ContentType(String value) {
        this.value = value;
    }

    public String asString() {
        return value;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof ContentType) {
            ContentType that = (ContentType) o;

            return java.util.Objects.equals(this.value, that.value);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return java.util.Objects.hash(value);
    }
}
