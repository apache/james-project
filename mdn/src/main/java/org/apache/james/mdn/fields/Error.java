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

package org.apache.james.mdn.fields;

import java.util.Objects;

import com.google.common.base.Preconditions;

/**
 * Implements the optional MDN Error field defined in RFC-8098
 *
 * https://tools.ietf.org/html/rfc8098#section-3.2.7
 */
public class Error implements Field {
    public static final String FIELD_NAME = "Error";

    private final Text text;

    public Error(Text text) {
        Preconditions.checkNotNull(text);
        this.text = text;
    }

    public Text getText() {
        return text;
    }

    @Override
    public String formattedValue() {
        return FIELD_NAME + ": " + text.formatted();
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof Error) {
            Error error = (Error) o;

            return Objects.equals(text, error.text);
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return Objects.hash(text);
    }

    @Override
    public String toString() {
        return formattedValue();
    }
}
