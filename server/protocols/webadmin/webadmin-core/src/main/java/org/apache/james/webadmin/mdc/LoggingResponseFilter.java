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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Filter;
import spark.Request;
import spark.Response;

public class LoggingResponseFilter implements Filter {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingResponseFilter.class);
    private static final String STATUS = "status";
    private static final String BODY = "body";

    @Override
    public void handle(Request request, Response response) throws Exception {
        try (Closeable closeable =
                 MDCBuilder.create()
                     .addContext(STATUS, response.status())
                     .addContext(BODY, response.body())
                     .build()) {
            LOGGER.info("Received request");
        }
    }
}
