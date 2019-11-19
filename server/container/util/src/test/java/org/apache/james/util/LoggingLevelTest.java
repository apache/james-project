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

package org.apache.james.util;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class LoggingLevelTest {
    @Test
    void formatShouldCallDebugWhenDebugLevelIsGiven() {
        Logger logger = mock(Logger.class);
        LoggingLevel.DEBUG.format(logger, "easy as {} {} {}", 1, 2.0, "3");
        verify(logger).debug("easy as {} {} {}", 1, 2.0, "3");
    }

    @Test
    void formatShouldCallErrorWhenErrorLevelIsGiven() {
        Logger logger = mock(Logger.class);
        LoggingLevel.ERROR.format(logger, "easy as {} {} {}", 1, 2.0, "3");
        verify(logger).error("easy as {} {} {}", 1, 2.0, "3");
    }

    @Test
    void formatShouldCallInfoWhenInfoLevelIsGiven() {
        Logger logger = mock(Logger.class);
        LoggingLevel.INFO.format(logger, "easy as {} {} {}", 1, 2.0, "3");
        verify(logger).info("easy as {} {} {}", 1, 2.0, "3");
    }

    @Test
    void formatShouldCallTraceWhenTraceLevelIsGiven() {
        Logger logger = mock(Logger.class);
        LoggingLevel.TRACE.format(logger, "easy as {} {} {}", 1, 2.0, "3");
        verify(logger).trace("easy as {} {} {}", 1, 2.0, "3");
    }

    @Test
    void formatShouldCallWarningWhenWarningLevelIsGiven() {
        Logger logger = mock(Logger.class);
        LoggingLevel.WARNING.format(logger, "easy as {} {} {}", 1, 2.0, "3");
        verify(logger).warn("easy as {} {} {}", 1, 2.0, "3");
    }
}
