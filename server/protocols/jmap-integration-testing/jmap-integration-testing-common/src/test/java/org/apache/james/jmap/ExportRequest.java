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

package org.apache.james.jmap;

public class ExportRequest {

    public static class Builder {

        @FunctionalInterface
        public interface RequireSharee {
            RequireMatchingQuery exportTo(String sharee);
        }

        @FunctionalInterface
        public interface RequireMatchingQuery {
            ExportRequest query(String query);
        }
    }

    public static Builder.RequireSharee userExportFrom(String userExportFrom) {
        return sharee -> query -> new ExportRequest(userExportFrom, sharee, query);
    }

    private final String userExportFrom;
    private final String sharee;
    private final String matchingQuery;

    private ExportRequest(String userExportFrom, String sharee, String matchingQuery) {
        this.userExportFrom = userExportFrom;
        this.sharee = sharee;
        this.matchingQuery = matchingQuery;
    }

    public String getUserExportFrom() {
        return userExportFrom;
    }

    public String getSharee() {
        return sharee;
    }

    public String getMatchingQuery() {
        return matchingQuery;
    }
}
