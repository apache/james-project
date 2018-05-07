package org.apache.james.eventsourcing;

import static org.apache.james.eventsourcing.TestAggregateId.testId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import nl.jqno.equalsverifier.EqualsVerifier;

public interface EventStoreTest {

    TestAggregateId AGGREGATE_1 = testId(1);
    TestAggregateId AGGREGATE_2 = testId(2);

    @Test
    default void historyShouldMatchBeanContract() {
        EqualsVerifier.forClass(EventStore.History.class)
            .allFieldsShouldBeUsed()
            .verify();
    }

    @Test
    default void getEventsOfAggregateShouldThrowOnNullAggregateId(EventStore testee) {
        assertThatThrownBy(() -> testee.getEventsOfAggregate(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void appendShouldThrowWhenEventFromSeveralAggregates(EventStore testee) {
        TestEvent event1 = new TestEvent(EventId.first(), AGGREGATE_1, "first");
        TestEvent event2 = new TestEvent(event1.eventId().next(), AGGREGATE_2, "second");
        assertThatThrownBy(() -> testee.appendAll(event1, event2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    default void appendShouldDoNothingOnEmptyEventList(EventStore testee) {
        assertThatCode(testee::appendAll).doesNotThrowAnyException();
    }

    @Test
    default void appendShouldThrowWhenTryingToRewriteHistory(EventStore testee) {
        TestEvent event1 = new TestEvent(EventId.first(), AGGREGATE_1, "first");
        testee.append(event1);
        TestEvent event2 = new TestEvent(EventId.first(), AGGREGATE_1, "second");
        assertThatThrownBy(() -> testee.append(event2)).isInstanceOf(EventStore.EventStoreFailedException.class);
    }

    @Test
    default void getEventsOfAggregateShouldReturnEmptyHistoryWhenUnknown(EventStore testee) {
        assertThat(testee.getEventsOfAggregate(AGGREGATE_1)).isEqualTo(EventStore.History.empty());
    }

    @Test
    default void getEventsOfAggregateShouldReturnAppendedEvent(EventStore testee) {
        TestEvent event = new TestEvent(EventId.first(), AGGREGATE_1, "first");
        testee.append(event);
        assertThat(testee.getEventsOfAggregate(AGGREGATE_1))
            .isEqualTo(EventStore.History.of(ImmutableList.of(event)));
    }

    @Test
    default void getEventsOfAggregateShouldReturnAppendedEvents(EventStore testee) {
        TestEvent event1 = new TestEvent(EventId.first(), AGGREGATE_1, "first");
        TestEvent event2 = new TestEvent(event1.eventId().next(), AGGREGATE_1, "second");
        testee.append(event1);
        testee.append(event2);
        assertThat(testee.getEventsOfAggregate(AGGREGATE_1))
            .isEqualTo(EventStore.History.of(ImmutableList.of(event1, event2)));
    }

}