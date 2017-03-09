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

package org.apache.james.webadmin;

import spark.Filter;
import spark.Request;
import spark.Response;


public class CORSFilter implements Filter {
    private final String urlCORSOrigin;

    public CORSFilter(String urlCORSOrigin) {
        this.urlCORSOrigin = urlCORSOrigin;
    }

    @Override
    public void handle(Request request, Response response) throws Exception {
            response.header("Access-Control-Allow-Origin", urlCORSOrigin);
            response.header("Access-Control-Request-Method", "DELETE, GET, POST, PUT");
            response.header("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept");
    }
}
