package org.apache.mailbox.tools.indexer;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.apache.james.mailbox.model.TestId;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

class SingleMailboxReindexingTaskTest {

    @Test
    void parametersShouldReturnMapWithStringValues() {
        ReIndexerPerformer reIndexerPerformer = mock(ReIndexerPerformer.class);
        TestId mailboxId = TestId.of(1L);
        SingleMailboxReindexingTask task = new SingleMailboxReindexingTask(reIndexerPerformer, mailboxId);
        assertThat(task.parameters()).isEqualTo(ImmutableMap.of("mailboxId", "1"));
    }

}