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
package org.apache.james.modules.data;

import static org.apache.james.modules.data.JPAConfiguration.Credential.NO_CREDENTIAL;
import static org.apache.james.modules.data.JPAConfiguration.ReadyToBuild.NO_TEST_ON_BORROW;
import static org.apache.james.modules.data.JPAConfiguration.ReadyToBuild.NO_VALIDATION_QUERY;
import static org.apache.james.modules.data.JPAConfiguration.ReadyToBuild.NO_VALIDATION_QUERY_TIMEOUT_SEC;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class JPAConfiguration {

    public static class Credential {
        private static final Logger LOGGER = LoggerFactory.getLogger(Credential.class);
        static final Optional<Credential> NO_CREDENTIAL = Optional.empty();

        public static Optional<Credential> of(String username, String password) {
            if (StringUtils.isBlank(username) && StringUtils.isBlank(password)) {
                LOGGER.debug("username and password are blank, returns no credential by default");
                return NO_CREDENTIAL;
            }

            return Optional.of(new Credential(username, password));
        }

        private final String username;
        private final String password;

        private Credential(String username, String password) {
            Preconditions.checkArgument(StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password),
                "username and password for connecting to database can't be blank");
            this.username = username;
            this.password = password;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }
    }

    @FunctionalInterface
    public interface RequireDriverName {
        RequireDriverURL driverName(String driverName);
    }

    @FunctionalInterface
    public interface RequireDriverURL {
        ReadyToBuild driverURL(String driverUrl);
    }

    public static class ReadyToBuild {
        static final Optional<Boolean> NO_TEST_ON_BORROW = Optional.empty();
        static final Optional<Integer> NO_VALIDATION_QUERY_TIMEOUT_SEC = Optional.empty();
        static final Optional<String> NO_VALIDATION_QUERY = Optional.empty();

        private final String driverName;
        private final String driverURL;

        private Optional<Credential> credential;
        private Optional<Boolean> testOnBorrow;
        private Optional<Integer> validationQueryTimeoutSec;
        private Optional<String> validationQuery;


        private ReadyToBuild(String driverName, String driverURL, Optional<Credential> credential,
                            Optional<Boolean> testOnBorrow, Optional<Integer> validationQueryTimeoutSec,
                            Optional<String> validationQuery) {
            this.driverName = driverName;
            this.driverURL = driverURL;
            this.credential = credential;
            this.testOnBorrow = testOnBorrow;
            this.validationQueryTimeoutSec = validationQueryTimeoutSec;
            this.validationQuery = validationQuery;
        }

        public JPAConfiguration build() {
            return new JPAConfiguration(driverName, driverURL, credential, testOnBorrow, validationQueryTimeoutSec, validationQuery);
        }

        public RequirePassword username(String username) {
            return password -> new ReadyToBuild(driverName, driverURL, Credential.of(username, password),
                testOnBorrow, validationQueryTimeoutSec, validationQuery);
        }

        public ReadyToBuild testOnBorrow(Boolean testOnBorrow) {
            this.testOnBorrow = Optional.ofNullable(testOnBorrow);
            return this;
        }

        public ReadyToBuild validationQueryTimeoutSec(Integer validationQueryTimeoutSec) {
            this.validationQueryTimeoutSec = Optional.ofNullable(validationQueryTimeoutSec);
            return this;
        }

        public ReadyToBuild validationQuery(String validationQuery) {
            this.validationQuery = Optional.ofNullable(validationQuery);
            return this;
        }
    }

    @FunctionalInterface
    public interface RequirePassword {
        ReadyToBuild password(String password);
    }

    public static RequireDriverName builder() {
        return driverName -> driverURL -> new ReadyToBuild(driverName, driverURL, NO_CREDENTIAL, NO_TEST_ON_BORROW,
            NO_VALIDATION_QUERY_TIMEOUT_SEC, NO_VALIDATION_QUERY);
    }

    private final String driverName;
    private final String driverURL;
    private final Optional<Boolean> testOnBorrow;
    private final Optional<Integer> validationQueryTimeoutSec;
    private final Optional<Credential> credential;
    private final Optional<String> validationQuery;

    @VisibleForTesting
    JPAConfiguration(String driverName, String driverURL, Optional<Credential> credential, Optional<Boolean> testOnBorrow,
                     Optional<Integer> validationQueryTimeoutSec, Optional<String> validationQuery) {
        Preconditions.checkNotNull(driverName, "driverName cannot be null");
        Preconditions.checkNotNull(driverURL, "driverURL cannot be null");
        validationQueryTimeoutSec.ifPresent(timeoutInSec ->
            Preconditions.checkArgument(timeoutInSec > 0, "validationQueryTimeoutSec is required to be greater than 0"));

        this.driverName = driverName;
        this.driverURL = driverURL;
        this.credential = credential;
        this.testOnBorrow = testOnBorrow;
        this.validationQueryTimeoutSec = validationQueryTimeoutSec;
        this.validationQuery = validationQuery;
    }

    public String getDriverName() {
        return driverName;
    }

    public String getDriverURL() {
        return driverURL;
    }

    public Optional<Boolean> isTestOnBorrow() {
        return testOnBorrow;
    }

    public Optional<Integer> getValidationQueryTimeoutSec() {
        return validationQueryTimeoutSec;
    }

    public Optional<String> getValidationQuery() {
        return validationQuery;
    }

    public Optional<Credential> getCredential() {
        return credential;
    }
}
