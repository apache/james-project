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

package org.apache.james.blob.cassandra.utils;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;

import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import reactor.core.publisher.BaseSubscriber;

public class PipedStreamSubscriber extends BaseSubscriber<byte[]> {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final PipedInputStream in;
    private PipedOutputStream out;

    public PipedStreamSubscriber(PipedInputStream in) {
        Preconditions.checkNotNull(in, "The input stream must not be null");
        this.in = in;
    }

    @Override
    protected void hookOnSubscribe(Subscription subscription) {
        super.hookOnSubscribe(subscription);
        try {
            this.out = new PipedOutputStream(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    protected void hookOnNext(byte[] payload) {
        try {
            out.write(payload);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    protected void hookOnComplete() {
        close();
    }

    @Override
    protected void hookOnError(Throwable error) {
        logger.error("Failure processing stream", error);
        close();
    }

    @Override
    protected void hookOnCancel() {
        close();
    }

    private void close() {
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException ignored) {
            //ignored
        }
    }
}