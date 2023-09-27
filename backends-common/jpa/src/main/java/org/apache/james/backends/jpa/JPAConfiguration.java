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
package org.apache.james.backends.jpa;

import static org.apache.james.backends.jpa.JPAConfiguration.Credential.NO_CREDENTIAL;
import static org.apache.james.backends.jpa.JPAConfiguration.ReadyToBuild.CUSTOM_DATASOURCE_PROPERTIES;
import static org.apache.james.backends.jpa.JPAConfiguration.ReadyToBuild.CUSTOM_OPENJPA_PROPERTIES;
import static org.apache.james.backends.jpa.JPAConfiguration.ReadyToBuild.NO_ATTACHMENT_STORAGE;
import static org.apache.james.backends.jpa.JPAConfiguration.ReadyToBuild.NO_MAX_CONNECTIONS;
import static org.apache.james.backends.jpa.JPAConfiguration.ReadyToBuild.NO_MULTITHREADED;
import static org.apache.james.backends.jpa.JPAConfiguration.ReadyToBuild.NO_TEST_ON_BORROW;
import static org.apache.james.backends.jpa.JPAConfiguration.ReadyToBuild.NO_VALIDATION_QUERY;
import static org.apache.james.backends.jpa.JPAConfiguration.ReadyToBuild.NO_VALIDATION_QUERY_TIMEOUT_SEC;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class JPAConfiguration {
    public static final String JPA_CONNECTION_DRIVER_NAME = "openjpa.ConnectionDriverName";
    public static final String JPA_CONNECTION_USERNAME = "openjpa.ConnectionUserName";
    public static final String JPA_CONNECTION_PASSWORD = "openjpa.ConnectionPassword";
    public static final String JPA_CONNECTION_PROPERTIES = "openjpa.ConnectionProperties";
    public static final String JPA_CONNECTION_URL = "openjpa.ConnectionURL";
    public static final String JPA_MULTITHREADED = "openjpa.Multithreaded";
    public static final List<String> DEFAULT_JPA_PROPERTIES = List.of(JPA_CONNECTION_DRIVER_NAME, JPA_CONNECTION_URL, JPA_MULTITHREADED, JPA_CONNECTION_USERNAME, JPA_CONNECTION_PASSWORD);

    public static final String DATASOURCE_TEST_ON_BORROW = "datasource.testOnBorrow";
    public static final String DATASOURCE_VALIDATION_QUERY_TIMEOUT_SEC = "datasource.validationQueryTimeoutSec";
    public static final String DATASOURCE_VALIDATION_QUERY = "datasource.validationQuery";
    public static final String DATASOURCE_MAX_TOTAL = "datasource.maxTotal";
    public static final List<String> DEFAULT_DATASOURCE_PROPERTIES = List.of(DATASOURCE_TEST_ON_BORROW, DATASOURCE_VALIDATION_QUERY_TIMEOUT_SEC, DATASOURCE_VALIDATION_QUERY, DATASOURCE_MAX_TOTAL);

    public static final String ATTACHMENT_STORAGE = "attachmentStorage.enabled";

    static {
    }

    public JPAConfiguration() {
        this.driverName = "";
        this.driverURL = "";
        this.credential = Optional.empty();
        this.testOnBorrow = Optional.empty();
        this.multithreaded = Optional.empty();
        this.validationQueryTimeoutSec = Optional.empty();
        this.validationQuery = Optional.empty();
        this.maxConnections = Optional.empty();
        this.customDatasourceProperties = Map.of();
        this.customOpenjpaProperties = Map.of();
        this.attachmentStorage = Optional.empty();
    }

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
        static final Optional<Boolean> NO_MULTITHREADED = Optional.empty();
        static final Optional<Integer> NO_VALIDATION_QUERY_TIMEOUT_SEC = Optional.empty();
        static final Optional<String> NO_VALIDATION_QUERY = Optional.empty();
        static final Optional<Integer> NO_MAX_CONNECTIONS = Optional.empty();
        static final Map<String,String> CUSTOM_OPENJPA_PROPERTIES = Map.of();
        static final Map<String,String> CUSTOM_DATASOURCE_PROPERTIES = Map.of();
        static final Optional<Boolean> NO_ATTACHMENT_STORAGE = Optional.empty();

        private final String driverName;
        private final String driverURL;

        private Optional<Credential> credential;
        private Optional<Boolean> testOnBorrow;
        private Optional<Boolean> multithreaded;
        private Optional<Integer> validationQueryTimeoutSec;
        private Optional<String> validationQuery;
        private Optional<Integer> maxConnections;
        private Map<String,String> customDatasourceProperties;
        private Map<String,String> customOpenjpaProperties;
        private Optional<Boolean> attachmentStorage;


        private ReadyToBuild(String driverName, String driverURL, Optional<Credential> credential,
                            Optional<Boolean> testOnBorrow, Optional<Boolean> multithreaded, Optional<Integer> validationQueryTimeoutSec,
                            Optional<String> validationQuery,Optional<Integer> maxConnections,
                            Map<String,String> customDatasourceProperties, Map<String,String> customOpenjpaProperties,
                            Optional<Boolean> attachmentStorage
        ) {
            this.driverName = driverName;
            this.driverURL = driverURL;
            this.credential = credential;
            this.testOnBorrow = testOnBorrow;
            this.multithreaded = multithreaded;
            this.validationQueryTimeoutSec = validationQueryTimeoutSec;
            this.validationQuery = validationQuery;
            this.maxConnections = maxConnections;
            this.customDatasourceProperties = customDatasourceProperties;
            this.customOpenjpaProperties = customOpenjpaProperties;
            this.attachmentStorage = attachmentStorage;
        }

        public JPAConfiguration build() {
            return new JPAConfiguration(driverName, driverURL, credential, testOnBorrow, multithreaded, validationQueryTimeoutSec, validationQuery, maxConnections, customDatasourceProperties, customOpenjpaProperties, attachmentStorage);
        }

        public RequirePassword username(String username) {
            return password -> new ReadyToBuild(driverName, driverURL, Credential.of(username, password),
                testOnBorrow, multithreaded, validationQueryTimeoutSec, validationQuery, maxConnections, customDatasourceProperties, customOpenjpaProperties, attachmentStorage);
        }

        public ReadyToBuild testOnBorrow(Boolean testOnBorrow) {
            this.testOnBorrow = Optional.ofNullable(testOnBorrow);
            return this;
        }

        public ReadyToBuild multithreaded(Boolean multithreaded) {
            this.multithreaded = Optional.ofNullable(multithreaded);
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

        public ReadyToBuild maxConnections(Integer maxConnections) {
            this.maxConnections = Optional.ofNullable(maxConnections);
            return this;
        }

        public ReadyToBuild attachmentStorage(Boolean attachmentStorage) {
            this.attachmentStorage = Optional.ofNullable(attachmentStorage);
            return this;
        }

        public ReadyToBuild setCustomDatasourceProperties(Map<String, String> customDatasourceProperties) {
            this.customDatasourceProperties = new HashMap<>(customDatasourceProperties);
            DEFAULT_DATASOURCE_PROPERTIES.forEach(this.customDatasourceProperties::remove);
            return this;
        }

        public ReadyToBuild setCustomOpenjpaProperties(Map<String, String> customOpenjpaProperties) {
            this.customOpenjpaProperties = customOpenjpaProperties;
            DEFAULT_JPA_PROPERTIES.forEach(this.customOpenjpaProperties::remove);
            return this;
        }

    }

    @FunctionalInterface
    public interface RequirePassword {
        ReadyToBuild password(String password);
    }

    public static RequireDriverName builder() {
        return driverName -> driverURL -> new ReadyToBuild(driverName, driverURL, NO_CREDENTIAL, NO_TEST_ON_BORROW, NO_MULTITHREADED,
            NO_VALIDATION_QUERY_TIMEOUT_SEC, NO_VALIDATION_QUERY, NO_MAX_CONNECTIONS, CUSTOM_DATASOURCE_PROPERTIES, CUSTOM_OPENJPA_PROPERTIES, NO_ATTACHMENT_STORAGE);
    }

    private final String driverName;
    private final String driverURL;
    private final Optional<Boolean> testOnBorrow;
    private final Optional<Boolean> multithreaded;
    private final Optional<Integer> validationQueryTimeoutSec;
    private final Optional<Credential> credential;
    private final Optional<String> validationQuery;
    private final Optional<Integer> maxConnections;
    private Map<String,String> customDatasourceProperties;
    private Map<String,String> customOpenjpaProperties;
    private final Optional<Boolean> attachmentStorage;


    @VisibleForTesting
    JPAConfiguration(String driverName, String driverURL, Optional<Credential> credential, Optional<Boolean> testOnBorrow, Optional<Boolean> multithreaded,
                     Optional<Integer> validationQueryTimeoutSec, Optional<String> validationQuery, Optional<Integer> maxConnections, Map<String,String> customDatasourceProperties, Map<String,String> customOpenjpaProperties,
                     Optional<Boolean> attachmentStorage) {
        Preconditions.checkNotNull(driverName, "driverName cannot be null");
        Preconditions.checkNotNull(driverURL, "driverURL cannot be null");
        validationQueryTimeoutSec.ifPresent(timeoutInSec ->
            Preconditions.checkArgument(timeoutInSec > 0, "validationQueryTimeoutSec is required to be greater than 0"));
        maxConnections.ifPresent(maxCon ->
            Preconditions.checkArgument(maxCon == -1 || maxCon > 0, "maxConnections is required to be -1 (no limit) or  greater than 0"));

        this.driverName = driverName;
        this.driverURL = driverURL;
        this.credential = credential;
        this.testOnBorrow = testOnBorrow;
        this.multithreaded = multithreaded;
        this.validationQueryTimeoutSec = validationQueryTimeoutSec;
        this.validationQuery = validationQuery;
        this.maxConnections = maxConnections;
        this.customDatasourceProperties = customDatasourceProperties;
        this.customOpenjpaProperties = customOpenjpaProperties;
        this.attachmentStorage = attachmentStorage;

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

    public Optional<Boolean> isMultithreaded() {
        return multithreaded;
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

    public Map<String, String> getCustomOpenjpaProperties() {
        return customOpenjpaProperties;
    }

    public Map<String, String> getCustomDatasourceProperties() {
        return customDatasourceProperties;
    }

    public Optional<Integer> getMaxConnections() {
        return maxConnections;
    }

    public Optional<Boolean> isAttachmentStorageEnabled() {
        return attachmentStorage;
    }
}
