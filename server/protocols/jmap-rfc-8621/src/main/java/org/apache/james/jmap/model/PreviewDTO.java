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

package org.apache.james.jmap.model;
import java.util.Objects;
import java.util.Optional;

import org.apache.james.jmap.api.model.Preview;

import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

public class PreviewDTO {

    private static final String NO_BODY_AS_STRING = "(Empty)";

    public static final PreviewDTO NO_BODY = PreviewDTO.of(NO_BODY_AS_STRING);

    public static PreviewDTO from(Optional<Preview> preview) {
        return preview.map(Preview::getValue)
            .map(PreviewDTO::of)
            .orElse(NO_BODY);
    }

    @VisibleForTesting
    public static PreviewDTO of(String value) {
        return new PreviewDTO(value);
    }

    private final String value;

    private PreviewDTO(String value) {
        Preconditions.checkNotNull(value);

        this.value = Optional.of(value)
            .filter(previewValue -> !previewValue.isEmpty())
            .orElse(NO_BODY_AS_STRING);
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public final boolean equals(Object o) {
        if (o instanceof PreviewDTO) {
            PreviewDTO that = (PreviewDTO) o;

            return Objects.equals(this.value, that.value);
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
