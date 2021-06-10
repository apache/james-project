/******************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one     *
 * or more contributor license agreements.  See the NOTICE file   *
 * distributed with this work for additional information          *
 * regarding copyright ownership.  The ASF licenses this file     *
 * to you under the Apache License, Version 2.0 (the              *
 * "License"); you may not use this file except in compliance     *
 * with the License.  You may obtain a copy of the License at     *
 *                                                                *
 * http://www.apache.org/licenses/LICENSE-2.0                     *
 *                                                                *
 * Unless required by applicable law or agreed to in writing,     *
 * software distributed under the License is distributed on an    *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY         *
 * KIND, either express or implied.  See the License for the      *
 * specific language governing permissions and limitations        *
 * under the License.                                             *
 ******************************************************************/

package org.apache.james.httpclient;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import feign.Param;
import feign.RequestLine;
import feign.Response;

public interface DomainClient {
    class DomainAliasResponse {
        private final String source;

        @JsonCreator
        public DomainAliasResponse(@JsonProperty("source") String source) {
            this.source = source;
        }

        public String getSource() {
            return source;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof DomainAliasResponse) {
                DomainAliasResponse that = (DomainAliasResponse) o;

                return Objects.equals(this.source, that.source);
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(source);
        }
    }

    @RequestLine("GET")
    List<String> getDomainList();

    @RequestLine("PUT /{domainToBeCreated}")
    Response createADomain(@Param("domainToBeCreated") String domainName);

    @RequestLine("DELETE /{domainToBeDeleted}")
    Response deleteADomain(@Param("domainToBeDeleted") String domainName);

    @RequestLine("GET /{domainName}")
    Response doesExist(@Param("domainName") String domainName);

    @RequestLine("DELETE /{destinationDomain}/aliases/{sourceDomain}")
    Response deleteADomainAlias(@Param("destinationDomain") String destinationDomain, @Param("sourceDomain") String sourceDomain);

    @RequestLine("PUT /{destinationDomain}/aliases/{sourceDomain}")
    Response addADomainAlias(@Param("destinationDomain") String destinationDomain, @Param("sourceDomain") String sourceDomain);

    @RequestLine("GET /{domainName}/aliases")
    List<DomainAliasResponse> getDomainAliasList(@Param("domainName") String domainName);
}
