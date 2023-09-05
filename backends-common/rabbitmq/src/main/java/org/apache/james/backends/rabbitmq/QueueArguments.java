package org.apache.james.backends.rabbitmq;

import com.google.common.collect.ImmutableMap;

public class QueueArguments {
    private static final String SINGLE_ACTIVE_CONSUMER_ARGUMENT = "x-single-active-consumer";

    public static class Builder {
        @FunctionalInterface
        public interface RequiresReplicationFactor {
            Builder replicationFactor(int replicationFactor);
        }

        private final ImmutableMap.Builder<String, Object> arguments;

        public Builder() {
            arguments = ImmutableMap.builder();
        }

        public RequiresReplicationFactor quorumQueue() {
            arguments.put("x-queue-type", "quorum");
            return this::replicationFactor;
        }

        private Builder replicationFactor(int replicationFactor) {
            arguments.put("x-quorum-initial-group-size", replicationFactor);
            return this;
        }

        public Builder deadLetter(String deadLetterQueueName) {
            arguments.put("x-dead-letter-exchange", deadLetterQueueName);
            return this;
        }

        public Builder singleActiveConsumer() {
            arguments.put(SINGLE_ACTIVE_CONSUMER_ARGUMENT, true);
            return this;
        }

        public Builder queueTTL(long queueTTL) {
            arguments.put("x-expires", queueTTL);
            return this;
        }

        public ImmutableMap<String, Object> build() {
            return arguments.build();
        }
    }

    public static final ImmutableMap<String, Object> NO_ARGUMENTS = ImmutableMap.of();

    public static Builder builder() {
        return new Builder();
    }
}
