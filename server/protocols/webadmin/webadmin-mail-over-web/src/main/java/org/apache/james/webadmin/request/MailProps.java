package org.apache.james.webadmin.request;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import javax.activation.DataHandler;
import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.PreencodedMimeBodyPart;
import javax.mail.util.ByteArrayDataSource;

import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.server.core.MailImpl;
import org.apache.james.server.core.MimeMessageCopyOnWriteProxy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.fge.lambdas.Throwing;
import com.google.common.annotations.VisibleForTesting;

public class MailProps {

    private String name = UUID.randomUUID().toString();

    private String from;

    private List<String> tos;

    private List<String> ccs;

    private List<String> bccs;

    private String subject;

    private List<BodyPartProps> mailBodyPartProps = new ArrayList<>();

    private Map<String, String> headers = new HashMap<>();

    public MailProps(
            @JsonProperty("name") String name,
            @JsonProperty("from") String from,
            @JsonProperty("to") List<String> tos,
            @JsonProperty("cc") List<String> ccs,
            @JsonProperty("bcc") List<String> bccs,
            @JsonProperty("subject") String subject,
            @JsonProperty("bodyParts") List<BodyPartProps> bodyPartProps,
            @JsonProperty("headers") Map<String, String> headers) {
        Optional.ofNullable(name).ifPresent(this::setName);
        Optional.ofNullable(from).ifPresentOrElse(this::setFrom, () -> {
            throw new RuntimeException("Mail missing from");
        });
        Optional.ofNullable(tos).filter(Predicate.not(List::isEmpty)).ifPresentOrElse(this::setTos, () -> {
            throw new RuntimeException("Mail missing to addresses");
        });
        this.ccs = Optional.ofNullable(ccs).orElse(new ArrayList<>());
        this.bccs = Optional.ofNullable(bccs).orElse(new ArrayList<>());
        this.subject = subject;
        Optional.ofNullable(bodyPartProps).ifPresent(this::setMailBodyPartProps);
        Optional.ofNullable(headers).ifPresent(this::setHeaders);
    }

    public MailImpl asMailImpl() throws MessagingException, IOException {

        try {

            MimeMessage build = getMimeMessage();

            return MailImpl.fromMimeMessage(name, new MimeMessageCopyOnWriteProxy(build));
        } catch (AddressException e) {
            throw new RuntimeException("There is a problem with one mail address: " + e.getRef());
        }
    }

    public MimeMessage getMimeMessage() throws MessagingException, IOException {
        MimeMessageBuilder.MultipartBuilder multipartBuilder = MimeMessageBuilder.multipartBuilder();

        for (BodyPartProps mailBodyPartProps : this.mailBodyPartProps) {

            PreencodedMimeBodyPart preencodedMimeBodyPart = new PreencodedMimeBodyPart(Optional.ofNullable(mailBodyPartProps.getEncoding()).orElse("7bit"));

            Optional.ofNullable(mailBodyPartProps.getDisposition()).ifPresent(Throwing.consumer(preencodedMimeBodyPart::setDisposition));

            Optional.ofNullable(mailBodyPartProps.getFilename()).ifPresent(Throwing.consumer(preencodedMimeBodyPart::setFileName));

            preencodedMimeBodyPart.setDataHandler(
                    new DataHandler(
                            new ByteArrayDataSource(
                                    mailBodyPartProps.getContent(),
                                    mailBodyPartProps.getType()
                            )
                    )
            );

            Optional.ofNullable(mailBodyPartProps.getHeaders())
                    .ifPresent(headers -> {
                        headers.entrySet().stream().forEach(Throwing.consumer(e -> preencodedMimeBodyPart.addHeader(e.getKey(), e.getValue())));
                    });

            multipartBuilder.addBody(
                    preencodedMimeBodyPart
            );

        }

        MimeMessage build = MimeMessageBuilder.mimeMessageBuilder()
                .setContent(
                        multipartBuilder
                )
                .addFrom(from)
                .setSubject(subject)
                .addCcRecipient(ccs.stream().toArray(String[]::new))
                .addBccRecipient(bccs.stream().toArray(String[]::new))
                .addToRecipient(tos.stream().toArray(String[]::new))
                .addHeaders(headers.entrySet().stream().map(e -> new Header(e.getKey(), e.getValue())).toArray(MimeMessageBuilder.Header[]::new))
                .build();
        return build;
    }

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getFrom() {
        return this.from;
    }

    public void setFrom(final String from) {
        this.from = from;
    }

    public List<String> getTos() {
        return this.tos;
    }

    public void setTos(final List<String> tos) {
        this.tos = tos;
    }

    public List<String> getCcs() {
        return this.ccs;
    }

    public void setCcs(final List<String> ccs) {
        this.ccs = ccs;
    }

    public List<String> getBccs() {
        return this.bccs;
    }

    public void setBccs(final List<String> bccs) {
        this.bccs = bccs;
    }

    public String getSubject() {
        return this.subject;
    }

    public void setSubject(final String subject) {
        this.subject = subject;
    }

    public List<BodyPartProps> getMailBodyPartProps() {
        return this.mailBodyPartProps;
    }

    public void setMailBodyPartProps(final List<BodyPartProps> mailBodyPartProps) {
        this.mailBodyPartProps = mailBodyPartProps;
    }

    public Map<String, String> getHeaders() {
        return this.headers;
    }

    public void setHeaders(final Map<String, String> headers) {
        this.headers = headers;
    }
}
