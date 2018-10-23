package org.apache.james.transport.matchers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.core.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SenderDomainIsTest {

    private static final String SENDER_NAME = "test@james.apache.org";

    private SenderDomainIs matcher;
    private MailAddress recipient;

    @BeforeEach
    void setUp() throws Exception {
        matcher = new SenderDomainIs();
        recipient = new MailAddress("recipient@james.apache.org");
    }

    @Test
    void shouldMatchOnMatchingSenderDomain() throws Exception {
        matcher.init(FakeMatcherConfig.builder().matcherName("SenderDomainIs").condition(SENDER_NAME).build());

        FakeMail fakeMail = FakeMail.builder().sender(SENDER_NAME).recipient(recipient).build();

        assertThat(matcher.match(fakeMail)).containsExactly(recipient);
    }

    @Test
    void shouldNotMatchWhenWrongSenderDomain() throws Exception {
        matcher.init(FakeMatcherConfig.builder().matcherName("SenderDomainIs").condition(SENDER_NAME).build());

        FakeMail fakeMail = FakeMail.builder().recipient(recipient).sender("other@james.apache.org").build();

        assertThat(matcher.match(fakeMail)).isNull();
    }

    @Test
    void shouldNotMatchWhenNullSenderDomain() throws Exception {
        matcher.init(FakeMatcherConfig.builder().matcherName("SenderDomainIs").condition(SENDER_NAME).build());

        FakeMail fakeMail = FakeMail.builder().recipient(recipient).build();

        assertThat(matcher.match(fakeMail)).isNull();
    }

    @Test
    void initShouldThrowWhenEmptyCondition() {
        assertThatThrownBy(() -> matcher.init(FakeMatcherConfig.builder().matcherName("SenderDomainIs").build()))
                .isInstanceOf(NullPointerException.class);
    }
}
