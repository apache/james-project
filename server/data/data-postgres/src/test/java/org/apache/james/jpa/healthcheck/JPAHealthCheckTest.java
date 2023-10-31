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
package org.apache.james.jpa.healthcheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import org.apache.james.backends.jpa.JpaTestCluster;
import org.apache.james.core.healthcheck.Result;
import org.apache.james.core.healthcheck.ResultStatus;
import org.apache.james.mailrepository.jpa.model.JPAUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JPAHealthCheckTest {
    JPAHealthCheck jpaHealthCheck;
    JpaTestCluster jpaTestCluster;

    @BeforeEach
    void setUp() {
        jpaTestCluster = JpaTestCluster.create(JPAUrl.class);
        jpaHealthCheck = new JPAHealthCheck(jpaTestCluster.getEntityManagerFactory());
    }

    @Test
    void testWhenActive() {
        Result result = jpaHealthCheck.check().block();
        ResultStatus healthy = ResultStatus.HEALTHY;
        assertThat(result.getStatus()).as("Result %s status should be %s", result.getStatus(), healthy)
                .isEqualTo(healthy);
    }

    @Test
    void testWhenInactive() {
        jpaTestCluster.getEntityManagerFactory().close();
        Result result = Result.healthy(jpaHealthCheck.componentName());
        try {
            result = jpaHealthCheck.check().block();
        } catch (IllegalStateException e) {
            fail("The exception of the EMF was not handled property.ª");
        }
        ResultStatus unhealthy = ResultStatus.UNHEALTHY;
        assertThat(result.getStatus()).as("Result %s status should be %s", result.getStatus(), unhealthy)
                .isEqualTo(unhealthy);
    }
}
