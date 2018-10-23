package org.apache.james.transport.matchers;

import static org.apache.mailet.base.MailAddressFixture.ANY_AT_JAMES;
import static org.apache.mailet.base.MailAddressFixture.ANY_AT_JAMES2;
import static org.apache.mailet.base.MailAddressFixture.OTHER_AT_JAMES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.mail.MessagingException;

import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RecipientDomainIsTest {

    private RecipientDomainIs matcher;

    @BeforeEach
    void setUp() {
        matcher = new RecipientDomainIs();
    }

    @Test
    void shouldMatchOneAddress() throws MessagingException {
        matcher.init(FakeMatcherConfig.builder().matcherName("RecipientDomainIs").condition(ANY_AT_JAMES.toString())
                .build());
        FakeMail fakeMail = FakeMail.builder().recipient(ANY_AT_JAMES).build();
        assertThat(matcher.match(fakeMail)).containsOnly(ANY_AT_JAMES);
    }

    @Test
    void shouldOnlyMatchCorrespondingAddress() throws MessagingException {
        matcher.init(FakeMatcherConfig.builder().matcherName("RecipientDomainIs").condition(ANY_AT_JAMES.toString())
                .build());

        FakeMail fakeMail = FakeMail.builder().recipients(ANY_AT_JAMES, OTHER_AT_JAMES).build();

        assertThat(matcher.match(fakeMail)).containsOnly(ANY_AT_JAMES);
    }

    @Test
    void shouldNotMatchUnrelatedAddresses() throws Exception {
        matcher.init(FakeMatcherConfig.builder().matcherName("RecipientDomainIs").condition(ANY_AT_JAMES.toString())
                .build());

        FakeMail fakeMail = FakeMail.builder().recipients(OTHER_AT_JAMES, ANY_AT_JAMES2).build();

        assertThat(matcher.match(fakeMail)).isEmpty();
    }

    @Test
    void initShouldThrowOnMissingCondition() {
        assertThatThrownBy(() -> matcher.init(FakeMatcherConfig.builder().matcherName("RecipientDomainIs").build()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldBeAbleToMatchSeveralAddresses() throws Exception {
        matcher.init(FakeMatcherConfig.builder().matcherName("RecipientDomainIs")
                .condition(ANY_AT_JAMES + ", " + ANY_AT_JAMES2).build());

        FakeMail fakeMail = FakeMail.builder().recipients(ANY_AT_JAMES, OTHER_AT_JAMES, ANY_AT_JAMES2).build();

        assertThat(matcher.match(fakeMail)).containsOnly(ANY_AT_JAMES, ANY_AT_JAMES2);
    }
}
