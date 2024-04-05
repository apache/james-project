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

package org.apache.james.jmap.model.message.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.jmap.model.BlobId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class BlobIdTest {

    @Test
    public void shouldNotAllowEmptyString() {
        assertThatThrownBy(() -> BlobId.of("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldNotAllowNullInput() {
        assertThatThrownBy(() -> BlobId.of((String) null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldCreateInstanceWhenSimpleString() {
        assertThat(BlobId.of("simple string")).extracting(BlobId::getRawValue).isEqualTo("simple string");
    }

    @Test
    public void shouldRespectJavaBeanContract() {
        EqualsVerifier.forClass(BlobId.class).verify();
    }
}
