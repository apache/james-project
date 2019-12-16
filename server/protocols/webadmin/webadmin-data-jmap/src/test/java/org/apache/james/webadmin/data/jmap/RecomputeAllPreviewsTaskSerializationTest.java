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

package org.apache.james.webadmin.data.jmap;

import static org.mockito.Mockito.mock;

import org.apache.james.JsonSerializationVerifier;
import org.apache.james.util.ClassLoaderUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class RecomputeAllPreviewsTaskSerializationTest {
    MessageFastViewProjectionCorrector corrector;

    @BeforeEach
    void setUp() {
        corrector = mock(MessageFastViewProjectionCorrector.class);
    }

    @Test
    void shouldMatchJsonSerializationContract() throws Exception {
        JsonSerializationVerifier.dtoModule(RecomputeAllPreviewsTask.module(corrector))
            .bean(new RecomputeAllPreviewsTask(corrector))
            .json(ClassLoaderUtils.getSystemResourceAsString("json/recomputeAll.task.json"))
            .verify();
    }

    @Test
    void shouldMatchBeanContract() {
        EqualsVerifier.forClass(RecomputeAllPreviewsTask.class)
            .withIgnoredFields("corrector", "progress")
            .verify();
    }
}