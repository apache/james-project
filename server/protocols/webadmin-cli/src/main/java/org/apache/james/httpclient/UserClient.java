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

import org.apache.james.httpclient.model.UserName;
import org.apache.james.httpclient.model.UserPassword;

import feign.Headers;
import feign.Param;
import feign.RequestLine;
import feign.Response;

public interface UserClient {

    @RequestLine("GET")
    List<UserName> getUserNameList();

    @RequestLine("PUT /{userName}")
    @Headers("Content-Type: application/json")
    Response createAUser(@Param("userName") String userName, UserPassword password);

    @RequestLine("PUT /{userName}?force")
    @Headers("Content-Type: application/json")
    Response updateAUserPassword(@Param("userName") String userName, UserPassword password);

    @RequestLine("DELETE /{userToBeDeleted}")
    Response deleteAUser(@Param("userToBeDeleted") String userName);

    @RequestLine("HEAD /{userName}")
    Response doesExist(@Param("userName") String userName);

}