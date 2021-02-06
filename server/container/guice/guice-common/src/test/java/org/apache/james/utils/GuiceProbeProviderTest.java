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
package org.apache.james.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;

public class GuiceProbeProviderTest {
    private GuiceProbeProvider guiceProbeProvider;
    private GuiceProbe1 guiceProbe1;

    @BeforeEach
    void setUp() {
        guiceProbe1 = new GuiceProbe1();
        guiceProbeProvider = new GuiceProbeProvider(ImmutableSet.of(guiceProbe1));
    }

    @Test
    void getProveShouldThrowExcpetionWhenNull() {
        assertThatThrownBy(() -> guiceProbeProvider.getProbe(null))
            .isInstanceOf(NullPointerException.class);
    }
    
    @Test
    void getProbeShouldThrowRuntimeExceptionWhenEmpty() {
        assertThatThrownBy(() -> guiceProbeProvider.getProbe(GuiceProbe2.class))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void getProbeShouldReturnRightProbe() {
        assertThat(guiceProbeProvider.getProbe(GuiceProbe1.class)).isEqualTo(guiceProbe1);
    }

    static class GuiceProbe1 implements GuiceProbe {

    }

    static class GuiceProbe2 implements GuiceProbe {

    }
}