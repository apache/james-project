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

package org.apache.james.protocols.lmtp.core;

import java.util.Collection;

import jakarta.inject.Inject;

import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.smtp.core.esmtp.EhloCmdHandler;

import com.google.common.collect.ImmutableSet;

/**
 * Handles the LHLO command
 */
public class LhloCmdHandler extends EhloCmdHandler {

    private static final Collection<String> COMMANDS = ImmutableSet.of("LHLO");

    @Inject
    public LhloCmdHandler(MetricFactory metricFactory) {
        super(metricFactory);
    }

    @Override
    public Collection<String> getImplCommands() {
        return COMMANDS;
    }

}
