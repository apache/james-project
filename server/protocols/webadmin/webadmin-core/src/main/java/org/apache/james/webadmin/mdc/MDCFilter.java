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

package org.apache.james.webadmin.mdc;

import java.io.Closeable;

import org.apache.james.util.MDCBuilder;

import spark.Filter;
import spark.Request;
import spark.Response;

public class MDCFilter implements Filter {
    public static final String VERB = "verb";
    public static final String MDC_CLOSEABLE = "MDCCloseable";

    @Override
    public void handle(Request request, Response response) throws Exception {
        Closeable mdcCloseable = MDCBuilder.create()
            .addContext(MDCBuilder.IP, request.ip())
            .addContext(MDCBuilder.HOST, request.host())
            .addContext(VERB, request.requestMethod())
            .addContext(MDCBuilder.PROTOCOL, "webadmin")
            .addContext(MDCBuilder.ACTION, request.pathInfo())
            .build();
        request.attribute(MDC_CLOSEABLE, mdcCloseable);
    }
}
