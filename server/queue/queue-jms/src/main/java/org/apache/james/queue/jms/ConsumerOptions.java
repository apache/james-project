package org.apache.james.queue.jms;

import org.apache.james.queue.api.MailQueue;

/**
 * Provides additional options when creating consumers.
 */
public interface ConsumerOptions {
    /**
     * The empty consumer options.
     */
    static ConsumerOptions empty() {
        return queueName -> queueName;
    }

    /**
     * The queue name with options for performing {@link MailQueue#deQueue()} operation. The implementation may
     * return {@code queueName} itself if it does not support any options.
     *
     * @return The queue name maybe with additional options.
     */
    String applyForDequeue(String queueName);
}
