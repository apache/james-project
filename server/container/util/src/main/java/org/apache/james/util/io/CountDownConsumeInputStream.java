/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 * *
 * http://www.apache.org/licenses/LICENSE-2.0                 *
 * *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.util.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;

public class CountDownConsumeInputStream extends InputStream {
    private static final int RETURNED_VALUE = 0;

    private final CountDownLatch startSignal;

    public CountDownConsumeInputStream(CountDownLatch startSignal) {
        this.startSignal = startSignal;
    }

    public CountDownLatch getStartSignal() {
        return startSignal;
    }

    @Override
    public int read() throws IOException {
        if (startSignal.getCount() > 0) {
            return RETURNED_VALUE;
        }
        return -1;
    }
}
