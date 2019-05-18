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

package org.apache.james.backends.es.v6;

import java.util.concurrent.CompletableFuture;

import org.elasticsearch.action.ActionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListenerToFuture<T> implements ActionListener<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ListenerToFuture.class);

    private CompletableFuture<T> future;

    public ListenerToFuture() {
        this.future = new CompletableFuture<>();
    }

    @Override
    public void onResponse(T t) {
        future.complete(t);
    }

    @Override
    public void onFailure(Exception e) {
        LOGGER.warn("Error while waiting ElasticSearch query execution: ", e);
        future.completeExceptionally(e);
    }

    public CompletableFuture<T> getFuture() {
        return future;
    }
}
