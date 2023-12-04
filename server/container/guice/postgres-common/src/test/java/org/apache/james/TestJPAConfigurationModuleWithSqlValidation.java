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

package org.apache.james;

import static org.apache.james.TestJPAConfigurationModule.JDBC_EMBEDDED_DRIVER;

import javax.inject.Singleton;

import org.apache.james.backends.jpa.JPAConfiguration;
import org.apache.james.backends.postgres.PostgresExtension;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

public interface TestJPAConfigurationModuleWithSqlValidation {

    class NoDatabaseAuthentication extends AbstractModule {
        private final PostgresExtension postgresExtension;

        public NoDatabaseAuthentication(PostgresExtension postgresExtension) {
            this.postgresExtension = postgresExtension;
        }

        @Override
        protected void configure() {
        }

        @Provides
        @Singleton
        JPAConfiguration provideConfiguration() {
            return JPAConfiguration.builder()
                .driverName(JDBC_EMBEDDED_DRIVER)
                .driverURL(postgresExtension.getJdbcUrl())
                .testOnBorrow(true)
                .validationQueryTimeoutSec(2)
                .validationQuery(VALIDATION_SQL_QUERY)
                .build();
        }
    }

    class WithDatabaseAuthentication extends AbstractModule {
        private final PostgresExtension postgresExtension;

        public WithDatabaseAuthentication(PostgresExtension postgresExtension) {
            this.postgresExtension = postgresExtension;
        }

        @Override
        protected void configure() {

        }

        @Provides
        @Singleton
        JPAConfiguration provideConfiguration() {
            return JPAConfiguration.builder()
                .driverName(JDBC_EMBEDDED_DRIVER)
                .driverURL(postgresExtension.getJdbcUrl())
                .testOnBorrow(true)
                .validationQueryTimeoutSec(2)
                .validationQuery(VALIDATION_SQL_QUERY)
                .username(postgresExtension.getPostgresConfiguration().getCredential().getUsername())
                .password(postgresExtension.getPostgresConfiguration().getCredential().getPassword())
                .build();
        }
    }

    String VALIDATION_SQL_QUERY = "VALUES 1";
}
