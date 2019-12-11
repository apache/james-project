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

package org.apache.james.webadmin.dto;

import org.apache.james.backends.cassandra.versions.SchemaVersion;

import com.google.common.base.Preconditions;

public class CassandraVersionRequest {
    public static CassandraVersionRequest parse(String version) {
        Preconditions.checkNotNull(version, "Version is mandatory");
        try {
            return new CassandraVersionRequest(Integer.parseInt(version));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expecting version to be specified as an integer", e);
        }
    }

    private final int value;

    private CassandraVersionRequest(int value) {
        Preconditions.checkArgument(value > 0);
        this.value = value;
    }

    public SchemaVersion getValue() {
        return new SchemaVersion(value);
    }
}
