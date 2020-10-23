package org.apache.james.request;

import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.Arrays;

import javax.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.server.core.MailImpl;
import org.apache.james.server.core.MimeMessageCopyOnWriteProxy;
import org.apache.james.webadmin.request.BodyPartProps;
import org.apache.james.webadmin.request.MailProps;
import org.apache.mailet.Matcher;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MailPropsTest {

    @Test
    public void mailImplemantationHascorrectFieldsWhenBuiltFromMailProps() throws Exception{
        MailProps mailProps = new MailProps(
                "test",
                "valid@address",
                Arrays.asList("valid1@address"),
                Arrays.asList("valid2@address"),
                Arrays.asList("valid3@address"),
                "test-subject",
                null,
                null
        );

        MailImpl mail = mailProps.asMailImpl();

        assertThat(mail.getName(), Matchers.equalTo("test"));
        assertThat(mail.getSender().asPrettyString(), Matchers.equalTo("<valid@address>"));
        assertThat(mail.getRecipients(), Matchers.contains(new MailAddress("valid1@address"), new MailAddress("valid2@address"), new MailAddress("valid3@address")));
        assertThat(mail.getMessage().getHeader("Subject"), Matchers.array(Matchers.equalTo("test-subject")));
        assertThat(mail.getMessage().getHeader("Content-Type"), Matchers.array(Matchers.equalTo("text/plain; charset=us-ascii")));
    }

    @Test
    public void mailImplemantationHascorrectFieldsWhenBuiltFromMailPropsWithBodyParts() throws Exception{
        MailProps mailProps = new MailProps(
                "test",
                "valid@address",
                Arrays.asList("valid1@address"),
                Arrays.asList("valid2@address"),
                Arrays.asList("valid3@address"),
                "test-subject",
                Arrays.asList(new BodyPartProps("text/plain", null, "test-text", null, null, null)),
                null
        );

        MailImpl mail = mailProps.asMailImpl();

        assertThat(mail.getName(), Matchers.equalTo("test"));
        assertThat(mail.getSender().asPrettyString(), Matchers.equalTo("<valid@address>"));
        assertThat(mail.getRecipients(), Matchers.contains(new MailAddress("valid1@address"), new MailAddress("valid2@address"), new MailAddress("valid3@address")));
        assertThat(mail.getMessage().getHeader("Subject"), Matchers.array(Matchers.equalTo("test-subject")));
        assertThat(mail.getMessage().getHeader("Content-Type"), Matchers.array(Matchers.containsString("multipart/mixed;")));
    }

    @Test
    public void constructionFailsWhenFromIsNotSet() throws Exception{
        Assertions.assertThrows(RuntimeException.class, () -> {
            MailProps mailProps = new MailProps(
                    "test",
                    null,
                    Arrays.asList("valid1@address"),
                    Arrays.asList("valid2@address"),
                    Arrays.asList("valid3@address"),
                    "test-subject",
                    Arrays.asList(new BodyPartProps("text/plain", null, "test-text", null, null, null)),
                    null
            );
        }, "Mail missing from");
    }

    @Test
    public void constructionFailsWhenToIsNotSet() throws Exception{
        Assertions.assertThrows(RuntimeException.class, () -> {
            MailProps mailProps = new MailProps(
                    "test",
                    "address@valid",
                    null,
                    Arrays.asList("valid2@address"),
                    Arrays.asList("valid3@address"),
                    "test-subject",
                    Arrays.asList(new BodyPartProps("text/plain", null, "test-text", null, null, null)),
                    null
            );
        }, "Mail missing to addresses");
    }

    @Test
    public void constructionFailsWhenToIsEmptyList() throws Exception{
        Assertions.assertThrows(RuntimeException.class, () -> {
            MailProps mailProps = new MailProps(
                    "test",
                    "address@valid",
                    new ArrayList<>(),
                    Arrays.asList("valid2@address"),
                    Arrays.asList("valid3@address"),
                    "test-subject",
                    Arrays.asList(new BodyPartProps("text/plain", null, "test-text", null, null, null)),
                    null
            );
        }, "Mail missing to addresses");
    }

    @Test
    public void buildingMailImplFailsWhenNotUsingValidMailaddresses() throws Exception{
        Assertions.assertThrows(RuntimeException.class, () -> {
            MailProps mailProps = new MailProps(
                    "test",
                    "address?¤#",
                    Arrays.asList("valid1@address"),
                    Arrays.asList("valid2@address"),
                    Arrays.asList("valid3@address"),
                    "test-subject",
                    Arrays.asList(new BodyPartProps("text/plain", null, "test-text", null, null, null)),
                    null
            );
            mailProps.asMailImpl();
        });
    }

}
