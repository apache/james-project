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

package org.apache.james.webadmin.httpclient;

import java.util.List;

import org.apache.james.webadmin.httpclient.feign.DomainFeignClient;
import org.apache.james.webadmin.httpclient.feign.JamesFeignException;
import org.apache.james.webadmin.httpclient.model.DomainAlias;

import feign.Response;

public class DomainClient {

    private final DomainFeignClient feignClient;

    public DomainClient(DomainFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    public List<String> getDomainList() {
        return feignClient.getDomainList();
    }

    public void createADomain(String domain) {
        try (Response response = feignClient.createADomain(domain)) {
            FeignHelper.checkResponse(response.status() == 204, "Create domain failed. " + FeignHelper.extractBody(response));
        }
    }

    public void deleteADomain(String domain) {
        try (Response response = feignClient.deleteADomain(domain)) {
            FeignHelper.checkResponse(response.status() == 204, "Delete domain failed. " + FeignHelper.extractBody(response));
        }
    }

    public boolean doesExist(String domain) {
        try (Response response = feignClient.doesExist(domain)) {
            switch (response.status()) {
                case 204:
                    return true;
                case 404:
                    return false;
                default:
                    throw new JamesFeignException("Check domain exist failed. " + FeignHelper.extractBody(response));
            }
        }
    }

    public void deleteADomainAlias(String destinationDomain, String sourceDomain) {
        try (Response response = feignClient.deleteADomainAlias(destinationDomain, sourceDomain)) {
            FeignHelper.checkResponse(response.status() == 204, "Delete a domain alias failed. " + FeignHelper.extractBody(response));
        }
    }

    public void addADomainAlias(String destinationDomain, String sourceDomain) {
        try (Response response = feignClient.addADomainAlias(destinationDomain, sourceDomain)) {
            FeignHelper.checkResponse(response.status() == 204, "Create a domain alias failed. " + FeignHelper.extractBody(response));
        }
    }

    public List<DomainAlias> getDomainAliasList(String domain) {
        return feignClient.getDomainAliasList(domain);
    }

}
