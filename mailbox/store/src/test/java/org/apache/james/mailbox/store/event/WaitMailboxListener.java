package org.apache.james.mailbox.store.event;

import org.apache.james.mailbox.MailboxListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

public class WaitMailboxListener implements MailboxListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(WaitMailboxListener.class);

    private final AtomicLong invocationCount;
    private final ExecutionMode executionMode;

    public WaitMailboxListener(ExecutionMode executionMode) {
        this.invocationCount = new AtomicLong(0);
        this.executionMode = executionMode;
    }

    public WaitMailboxListener() {
        this(ExecutionMode.ASYNCHRONOUS);
    }

    public AtomicLong getInvocationCount() {
        return invocationCount;
    }

    @Override
    public ListenerType getType() {
        return ListenerType.MAILBOX;
    }

    @Override
    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    @Override
    public void event(Event event) {
        try {
            Thread.sleep(100);
            invocationCount.incrementAndGet();
        } catch (InterruptedException e) {
            LOGGER.info("interrupted", e);
        }
    }
}
