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
package org.apache.james.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class OptionalConverterTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void fromGuavaShouldThrowWhenGuavaIsNull() {
        expectedException.expect(NullPointerException.class);
        OptionalConverter.fromGuava(null);
    }

    @Test
    public void fromGuavaShouldReturnEmptyWhenGuavaIsEmpty() {
        Optional<String> fromGuava = OptionalConverter.fromGuava(com.google.common.base.Optional.<String> absent());

        assertThat(fromGuava).isEmpty();
    }

    @Test
    public void fromGuavaShouldReturnNonEmptyWhenGuavaIsNonEmpty() {
        String value = "my string";
        Optional<String> fromGuava = OptionalConverter.fromGuava(com.google.common.base.Optional.of(value));

        assertThat(fromGuava).contains(value);
    }

    @Test
    public void toGuavaShouldThrowWhenGuavaIsNull() {
        expectedException.expect(NullPointerException.class);
        OptionalConverter.toGuava(null);
    }

    @Test
    public void toGuavaShouldReturnEmptyWhenGuavaIsEmpty() {
        com.google.common.base.Optional<String> toGuava = OptionalConverter.toGuava(Optional.<String> empty());

        assertThat(toGuava.isPresent()).isFalse();
    }

    @Test
    public void toGuavaShouldReturnNonEmptyWhenGuavaIsNonEmpty() {
        String value = "my string";
        com.google.common.base.Optional<String> toGuava = OptionalConverter.toGuava(Optional.of(value));

        assertThat(toGuava.get()).isEqualTo(value);
    }
}
