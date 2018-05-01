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

import org.apache.james.backends.jpa.JPAConstants;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class JPAConfiguration {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String driverName;
        private String driverURL;
        private boolean testOnBorrow;
        private int validationQueryTimeoutSec = JPAConstants.VALIDATION_NO_TIMEOUT;
        private String validationQuery;


        public Builder driverName(String driverName) {
            this.driverName = driverName;
            return this;
        }

        public Builder driverURL(String driverURL) {
            this.driverURL = driverURL;
            return this;
        }

        public Builder testOnBorrow(boolean testOnBorrow) {
            this.testOnBorrow = testOnBorrow;
            return this;
        }

        public Builder validationQueryTimeoutSec(int validationQueryTimeoutSec) {
            this.validationQueryTimeoutSec = validationQueryTimeoutSec;
            return this;
        }

        public Builder validationQuery(String validationQuery) {
            this.validationQuery = validationQuery;
            return this;
        }

        public JPAConfiguration build() {
            Preconditions.checkNotNull(driverName);
            Preconditions.checkNotNull(driverURL);
            return new JPAConfiguration(driverName, driverURL, testOnBorrow, validationQueryTimeoutSec, validationQuery);
        }
    }

    private final String driverName;
    private final String driverURL;
    private final boolean testOnBorrow;
    private final int validationQueryTimeoutSec;
    private final String validationQuery;

    @VisibleForTesting
    JPAConfiguration(String driverName, String driverURL, boolean testOnBorrow, int validationQueryTimeoutSec, String validationQuery) {
        this.driverName = driverName;
        this.driverURL = driverURL;
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

    public boolean isTestOnBorrow() {
        return testOnBorrow;
    }

    public int getValidationQueryTimeoutSec() {
        return validationQueryTimeoutSec;
    }

    public String getValidationQuery() {
        return validationQuery;
    }

}
