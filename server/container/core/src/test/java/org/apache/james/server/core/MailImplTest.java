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
package org.apache.james.server.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Date;
import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.mailet.AttributeName;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.ContractMailTest;
import org.apache.mailet.DsnParameters;
import org.apache.mailet.Mail;
import org.apache.mailet.PerRecipientHeaders;
import org.apache.mailet.base.MailAddressFixture;
import org.apache.mailet.base.test.MailUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;

public class MailImplTest extends ContractMailTest {

    @Override
    public MailImpl newMail() {
        return MailImpl.builder().name("mail-id").build();
    }

    private MimeMessage emptyMessage;

    @BeforeEach
    void setup() throws Exception {
        emptyMessage = MimeMessageBuilder.mimeMessageBuilder()
            .setText("")
            .build();
    }

    @Test
    void mailImplShouldHaveSensibleInitialValues() throws Exception {
        MailImpl mail = newMail();

        assertThat(mail.getName()).isEqualTo("mail-id");
        assertThat(mail.hasAttributes()).describedAs("no initial attributes").isFalse();
        assertThat(mail.getErrorMessage()).describedAs("no initial error").isNull();
        assertThat(mail.getLastUpdated()).isCloseTo(new Date(), TimeUnit.SECONDS.toMillis(2));
        assertThat(mail.getRecipients()).describedAs("no initial recipient").isNullOrEmpty();
        assertThat(mail.getRemoteAddr()).describedAs("initial remote address is localhost ip").isEqualTo("127.0.0.1");
        assertThat(mail.getRemoteHost()).describedAs("initial remote host is localhost").isEqualTo("localhost");
        assertThat(mail.getState()).describedAs("default initial state").isEqualTo(Mail.DEFAULT);
        assertThat(mail.getMessage()).isNull();
        assertThat(mail.getMaybeSender()).isEqualTo(MaybeSender.nullSender());
    }

    @Test
    void mailImplShouldThrowWhenComputingSizeOnDefaultInstance() {
        MailImpl mail = newMail();

        assertThatThrownBy(mail::getMessageSize).isInstanceOf(NullPointerException.class);
    }

    @Test
    void mailImplConstructionShouldSetDefaultValuesOnUnspecifiedFields() throws Exception {
        MailImpl mail = MailImpl.builder()
            .name(MailUtil.newId())
            .sender("sender@localhost")
            .build();

        MailImpl expected = newMail();
        assertThat(mail)
            .usingRecursiveComparison()
            .ignoringFields("sender", "name", "recipients", "lastUpdated")
            .isEqualTo(expected);
        assertThat(mail.getLastUpdated()).isCloseTo(new Date(), TimeUnit.SECONDS.toMillis(2));
    }

    @Test
    void mailImplConstructionShouldSetSpecifiedFields() throws Exception {
        String sender = "sender@localhost";
        String name = MailUtil.newId();
        MailImpl mail = MailImpl.builder()
            .name(name)
            .sender(sender)
            .build();

        assertThat(mail.getMaybeSender().get().asString()).isEqualTo(sender);
        assertThat(mail.getName()).isEqualTo(name);

     }

    @Test
    void mailImplConstructionWithMimeMessageShouldSetSpecifiedFields() throws Exception {
        String name = MailUtil.newId();
        String sender = "sender@localhost";

        MailImpl expected = MailImpl.builder()
            .name(name)
            .sender(sender)
            .build();

        MailImpl mail = MailImpl.builder()
            .name(name)
            .sender(sender)
            .mimeMessage(emptyMessage)
            .build();

        assertThat(mail)
            .usingRecursiveComparison()
            .ignoringFields("message", "lastUpdated")
            .isEqualTo(expected);
        assertThat(mail.getLastUpdated()).isCloseTo(new Date(), TimeUnit.SECONDS.toMillis(2));
    }

    @Test
    void mailImplConstructionWithMimeMessageShouldNotOverwriteMessageId() throws Exception {
        MailImpl mail = MailImpl.builder()
            .name(MailUtil.newId())
            .sender("sender@localhost")
            .mimeMessage(emptyMessage)
            .build();

        assertThat(mail.getMessage().getMessageID()).isEqualTo(emptyMessage.getMessageID());
    }

    @Test
    void duplicateFactoryMethodShouldGenerateNewObjectWithSameValuesButName() throws Exception {
        String name = MailUtil.newId();
        PerRecipientHeaders perRecipientSpecificHeaders = new PerRecipientHeaders();
        PerRecipientHeaders.Header perRecipientHeader = PerRecipientHeaders.Header.builder().name("a").value("b").build();
        perRecipientSpecificHeaders.addHeaderForRecipient(perRecipientHeader, new MailAddress("a@b.c"));
        MailImpl mail = MailImpl.builder()
            .name(name)
            .sender("sender@localhost")
            .mimeMessage(emptyMessage)
            .addAllHeadersForRecipients(perRecipientSpecificHeaders)
            .build();

        MailImpl duplicate = MailImpl.duplicate(mail);

        assertThat(duplicate)
            .isNotSameAs(mail)
            .usingRecursiveComparison()
            .ignoringFields("message", "name")
            .isEqualTo(mail);
        assertThat(duplicate.getName()).isNotEqualTo(name);
        assertThat(duplicate.getMessage().getInputStream()).hasSameContentAs(mail.getMessage().getInputStream());
        assertThat(mail.getPerRecipientSpecificHeaders()).isEqualTo(duplicate.getPerRecipientSpecificHeaders());
    }

    @Test
    void setAttributeShouldThrowOnNullAttributeName() {
        MailImpl mail = newMail();

        assertThatThrownBy(() -> mail.setAttribute(null, "toto"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void deriveNewNameShouldThrowOnNull() {
        assertThatThrownBy(() -> MailImpl.deriveNewName(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void deriveNewNameShouldGenerateNonEmptyStringOnEmpty() throws Exception {
        assertThat(MailImpl.deriveNewName("")).isNotEmpty();
    }

    @Test
    void deriveNewNameShouldNeverGenerateMoreThan86Characters() throws Exception {
        String longString = "mu1Eeseemu1Eeseemu1Eeseemu1Eeseemu1Eeseemu1Eeseemu1Eeseemu1Eeseemu1Eeseeseemu1Eesee";
        assertThat(MailImpl.deriveNewName(longString).length()).isLessThan(86);
    }

    @Test
    void deriveNewNameShouldThrowWhenMoreThan8NestedCalls() {
        String called6Times = IntStream.range(0, 8)
            .mapToObj(String::valueOf)
            .reduce("average value ", Throwing.binaryOperator((left, right) -> MailImpl.deriveNewName(left)));
        assertThatThrownBy(() -> MailImpl.deriveNewName(called6Times)).isInstanceOf(MessagingException.class);
    }

    @Test
    void deriveNewNameShouldThrowWhenMoreThan8NestedCallsAndSmallInitialValue() {
        String called6Times = IntStream.range(0, 8)
            .mapToObj(String::valueOf)
            .reduce("small", Throwing.binaryOperator((left, right) -> MailImpl.deriveNewName(left)));
        assertThatThrownBy(() -> MailImpl.deriveNewName(called6Times)).isInstanceOf(MessagingException.class);
    }

    @Test
    void deriveNewNameShouldThrowWhenMoreThan8NestedCallsAndLongInitialValue() {
        String called6Times = IntStream.range(0, 8)
            .mapToObj(String::valueOf)
            .reduce("looooooonnnnnngggggggggggggggg", Throwing.binaryOperator((left, right) -> MailImpl.deriveNewName(left)));
        assertThatThrownBy(() -> MailImpl.deriveNewName(called6Times)).isInstanceOf(MessagingException.class);
    }

    @Test
    void deriveNewNameShouldGenerateNotEqualsCurrentName() throws Exception {
        assertThat(MailImpl.deriveNewName("current")).isNotEqualTo("current");
    }

    @Test
    void getMaybeSenderShouldHandleNullSender() {
        assertThat(
            MailImpl.builder()
                .name("mail-id")
                .sender(MailAddress.nullSender())
                .build()
                .getMaybeSender())
            .isEqualTo(MaybeSender.nullSender());
    }

    @Test
    void getMaybeSenderShouldHandleNoSender() {
        assertThat(
            MailImpl.builder()
                .name("mail-id")
                .build()
                .getMaybeSender())
            .isEqualTo(MaybeSender.nullSender());
    }

    @Test
    void getMaybeSenderShouldHandleSender() {
        assertThat(
            MailImpl.builder()
                .name("mail-id")
                .sender(MailAddressFixture.SENDER)
                .build()
                .getMaybeSender())
            .isEqualTo(MaybeSender.of(MailAddressFixture.SENDER));
    }

    @Test
    void hasSenderShouldReturnFalseWhenSenderIsNull() {
        assertThat(
            MailImpl.builder()
                .name("mail-id")
                .sender(MailAddress.nullSender())
                .build()
                .hasSender())
            .isFalse();
    }

    @Test
    void hasSenderShouldReturnFalseWhenSenderIsNotSpecified() {
        assertThat(
            MailImpl.builder()
                .name("mail-id")
                .build()
                .hasSender())
            .isFalse();
    }

    @Test
    void hasSenderShouldReturnTrueWhenSenderIsSpecified() {
        assertThat(
            MailImpl.builder()
                .name("mail-id")
                .sender(MailAddressFixture.SENDER)
                .build()
                .hasSender())
            .isTrue();
    }

    @Test
    void builderShouldNotAllowNullName() {
        assertThatThrownBy(() -> MailImpl.builder().name(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void builderShouldNotAllowEmptyName() {
        assertThatThrownBy(() -> MailImpl.builder().name(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mailImplShouldNotAllowSettingNullName() {
        assertThatThrownBy(() -> newMail().setName(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void mailImplShouldNotAllowSettingEmptyName() {
        assertThatThrownBy(() -> newMail().setName(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void mailImplShouldBeSerializable() throws Exception {
        MailImpl mail = MailImpl.builder()
            .name("mail-id")
            .sender(MailAddress.nullSender())
            .build();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(mail);
        objectOutputStream.close();
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
        ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
        Object unserialized = objectInputStream.readObject();

        assertThat(unserialized)
            .isInstanceOf(MailImpl.class)
            .usingRecursiveComparison()
            .isEqualTo(mail);
    }

    @Test
    void mailImplShouldBeSerializableWithOptionalAttribute() throws Exception {
        MailImpl mail = MailImpl.builder()
            .name("mail-id")
            .sender(MailAddress.nullSender())
            .addAttribute(AttributeName.of("name").withValue(AttributeValue.of(Optional.empty())))
            .build();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(mail);
        objectOutputStream.close();
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
        ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
        Object unserialized = objectInputStream.readObject();

        assertThat(unserialized)
            .isInstanceOf(MailImpl.class)
            .usingRecursiveComparison()
            .isEqualTo(mail);
    }

    @Test
    void mailImplShouldBeSerializableWithCollectionAttribute() throws Exception {
        MailImpl mail = MailImpl.builder()
            .name("mail-id")
            .sender(MailAddress.nullSender())
            .addAttribute(AttributeName.of("name").withValue(AttributeValue.of(ImmutableList.of(AttributeValue.of("a")))))
            .build();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(mail);
        objectOutputStream.close();
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
        ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
        Object unserialized = objectInputStream.readObject();

        assertThat(unserialized)
            .isInstanceOf(MailImpl.class)
            .usingRecursiveComparison()
            .isEqualTo(mail);
    }

    @Test
    void mailShouldPreserveDsnParameters() throws Exception {
        DsnParameters dsnParameters = DsnParameters.builder()
            .envId(DsnParameters.EnvId.of("434554-55445-33443"))
            .ret(DsnParameters.Ret.FULL)
            .addRcptParameter(new MailAddress("bob@apache.org"), DsnParameters.RecipientDsnParameters.of(new MailAddress("andy@apache.org")))
            .addRcptParameter(new MailAddress("cedric@apache.org"), DsnParameters.RecipientDsnParameters.of(EnumSet.of(DsnParameters.Notify.SUCCESS)))
            .addRcptParameter(new MailAddress("domi@apache.org"), DsnParameters.RecipientDsnParameters.of(EnumSet.of(DsnParameters.Notify.FAILURE), new MailAddress("eric@apache.org")))
            .build().get();

        MailImpl mail = MailImpl.builder()
            .name("mail-id")
            .build();

        mail.setDsnParameters(dsnParameters);

        assertThat(mail.dsnParameters())
            .contains(dsnParameters);
    }

    @Test
    void setDsnParametersShouldUpdateStoredValue() throws Exception {
        DsnParameters dsnParameters1 = DsnParameters.builder()
            .envId(DsnParameters.EnvId.of("434554-55445-33443"))
            .ret(DsnParameters.Ret.FULL)
            .addRcptParameter(new MailAddress("bob@apache.org"), DsnParameters.RecipientDsnParameters.of(new MailAddress("andy@apache.org")))
            .addRcptParameter(new MailAddress("cedric@apache.org"), DsnParameters.RecipientDsnParameters.of(EnumSet.of(DsnParameters.Notify.SUCCESS)))
            .addRcptParameter(new MailAddress("domi@apache.org"), DsnParameters.RecipientDsnParameters.of(EnumSet.of(DsnParameters.Notify.FAILURE), new MailAddress("eric@apache.org")))
            .build().get();
        DsnParameters dsnParameters2 = DsnParameters.builder()
            .envId(DsnParameters.EnvId.of("434554-55445-33434ee4"))
            .addRcptParameter(new MailAddress("domi@apache.org"), DsnParameters.RecipientDsnParameters.of(EnumSet.of(DsnParameters.Notify.FAILURE), new MailAddress("eric@apache.org")))
            .build().get();

        MailImpl mail = MailImpl.builder()
            .name("mail-id")
            .build();

        mail.setDsnParameters(dsnParameters1);
        mail.setDsnParameters(dsnParameters2);

        assertThat(mail.dsnParameters())
            .contains(dsnParameters2);
    }

    @Test
    void dsnParametersShouldBeEmptyByDefault() {
        MailImpl mail = MailImpl.builder()
            .name("mail-id")
            .build();

        assertThat(mail.dsnParameters())
            .isEmpty();
    }
}
