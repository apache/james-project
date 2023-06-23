package org.apache.james.transport.matchers;

import org.apache.mailet.Matcher;
import org.apache.mailet.base.AutomaticallySentMailDetector;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.mail.MessagingException;

import static org.apache.mailet.base.MailAddressFixture.ANY_AT_JAMES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IsFromMailingListTest {

    private Matcher matcher;
    private AutomaticallySentMailDetector automaticallySentMailDetector;

    @BeforeEach
    public void setUp() throws MessagingException {
        automaticallySentMailDetector = mock(AutomaticallySentMailDetector.class);
        matcher = new IsFromMailingList(automaticallySentMailDetector);
        FakeMatcherConfig matcherConfig = FakeMatcherConfig.builder()
                .matcherName("IsFromMailingList")
                .build();
        matcher.init(matcherConfig);
    }

    @Test
    void matchShouldMatchFromMailingListEmails() throws MessagingException {
        FakeMail fakeMail = FakeMail.builder().name("mail").recipient(ANY_AT_JAMES).build();

        when(automaticallySentMailDetector.isMailingList(fakeMail)).thenReturn(true);

        assertThat(matcher.match(fakeMail)).containsExactly(ANY_AT_JAMES);
    }

    @Test
    void matchShouldNotMatchIfNotFromMailingListEmails() throws MessagingException {
        FakeMail fakeMail = FakeMail.builder().name("mail").recipient(ANY_AT_JAMES).build();

        when(automaticallySentMailDetector.isMailingList(fakeMail)).thenReturn(false);

        assertThat(matcher.match(fakeMail)).isEmpty();
    }

}