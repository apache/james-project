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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class JPAConfiguration {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String driverName;
        private String driverURL;

        public Builder driverName(String driverName) {
            this.driverName = driverName;
            return this;
        }

        public Builder driverURL(String driverURL) {
            this.driverURL = driverURL;
            return this;
        }

        public JPAConfiguration build() {
            Preconditions.checkNotNull(driverName);
            Preconditions.checkNotNull(driverURL);
            return new JPAConfiguration(driverName, driverURL);
        }
    }

    private final String driverName;
    private final String driverURL;

    @VisibleForTesting JPAConfiguration(String driverName, String driverURL) {
        this.driverName = driverName;
        this.driverURL = driverURL;
    }

    public String getDriverName() {
        return driverName;
    }

    public String getDriverURL() {
        return driverURL;
    }

}
