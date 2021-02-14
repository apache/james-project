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


package org.apache.james.mailets.configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

public class MailetContainer implements SerializableAsXml {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        public static final int DEFAULT_THREAD_COUNT = 5;
        public static final String DEFAULT_POSTMASTER = "postmaster@localhost";
        private Optional<String> postmaster;
        private Optional<Integer> threads;
        private Map<String, ProcessorConfiguration> processors;

        private Builder() {
            processors = new HashMap<>();
            threads = Optional.empty();
            postmaster = Optional.empty();
        }

        public Builder postmaster(String postmaster) {
            this.postmaster = Optional.of(postmaster);
            return this;
        }

        public Builder threads(int threads) {
            this.threads = Optional.of(threads);
            return this;
        }

        public Builder putProcessor(ProcessorConfiguration processorConfiguration) {
            this.processors.put(processorConfiguration.getState(), processorConfiguration);
            return this;
        }

        public Builder putProcessor(ProcessorConfiguration.Builder processorConfiguration) {
            return this.putProcessor(processorConfiguration.build());
        }

        public MailetContainer build() {
            String postmaster = this.postmaster.orElse(DEFAULT_POSTMASTER);
            int threads = this.threads.orElse(DEFAULT_THREAD_COUNT);
            Preconditions.checkState(!Strings.isNullOrEmpty(postmaster), "'postmaster' is mandatory");
            Preconditions.checkState(threads > 0, "'threads' should be greater than 0");
            return new MailetContainer(postmaster, threads,
                ImmutableList.copyOf(processors.values()));
        }
    }

    private final String postmaster;
    private final int threads;
    private final List<ProcessorConfiguration> processors;

    private MailetContainer(String postmaster, int threads, List<ProcessorConfiguration> processors) {
        this.postmaster = postmaster;
        this.threads = threads;
        this.processors = processors;
    }

    public String getPostmaster() {
        return postmaster;
    }

    public int getThreads() {
        return threads;
    }

    public List<ProcessorConfiguration> getProcessors() {
        return processors;
    }

    @Override
    public String serializeAsXml() {
        StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\"?>\n")
            .append("<mailetcontainer enableJmx=\"false\">\n")
            .append("<context><postmaster>").append(getPostmaster()).append("</postmaster>").append("</context>\n")
            .append("<spooler><threads>").append(getThreads()).append("</threads>").append("</spooler>\n")
            .append("<processors>\n");
        for (ProcessorConfiguration processorConfiguration : getProcessors()) {
            builder.append(processorConfiguration.serializeAsXml());
        }
        builder.append("</processors>\n")
            .append("</mailetcontainer>");
        return builder.toString();
    }
}
