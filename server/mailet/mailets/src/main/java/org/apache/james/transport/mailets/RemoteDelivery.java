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

package org.apache.james.transport.mailets;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.inject.Inject;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.queue.api.MailPrioritySupport;
import org.apache.james.queue.api.MailQueue;
import org.apache.james.queue.api.MailQueue.MailQueueException;
import org.apache.james.queue.api.MailQueueFactory;
import org.apache.james.transport.mailets.remote.delivery.Bouncer;
import org.apache.james.transport.mailets.remote.delivery.DeliveryRunnable;
import org.apache.james.transport.mailets.remote.delivery.RemoteDeliveryConfiguration;
import org.apache.james.util.AuditTrail;
import org.apache.mailet.Mail;
import org.apache.mailet.ProcessingState;
import org.apache.mailet.base.GenericMailet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * <p>The RemoteDelivery mailet delivers messages to a remote SMTP server able to deliver or forward messages to their final
 * destination.
 * <p/>
 * <p>The remote SMTP server through which each mail is delivered is resolved using MX lookup for each message destination
 * unless the <code>&lt;gateway/&gt</code> parameter is set. The <code>&lt;gateway/&gt</code> parameter enables the
 * definition of one or more gateway servers through which all messages are sent.
 * <p/>
 * <p>If an attempt to deliver a message fails, a redelivery attempt is scheduled according to the scheme defined
 * by the <code>&lt;delayTime/&gt</code> parameter, retrying up to the limit defined
 * by the <code>&lt;maxRetries/&gt</code> parameter. When the retry limit is exceeded, delivery failure is processed
 * according to the setting of the <code>&lt;bounceProcessor/&gt</code> parameter.
 * <p/>
 * <p>These are the parameters that control the operation of the RemoteDelivery mailet:
 * <p/>
 * <ul>
 * <li><b>outgoing</b> (required) - a String containing the name of the queue that will hold messages being processed by this mailet.</li>
 * <li><b>bind</b> (optional) - a String describing the local IP address to which the mailet should be bound while delivering
 * emails. This tag is useful for multihomed machines. Default is to bind to the default local address of the machine.<br>
 * Note: The same IP address must be used for all of those RemoteDelivery instances where you explicitly supply a bind address.
 * <li><b>delayTime</b> (optional> a String containing a comma separated list of patterns defining the number of and delays between delivery
 * attempts. The pattern is <code>[attempts\*]delay [unit]</code> where:
 * <ul>
 * <li><i>attempts</i> (optional) - an Integer for the number of delivery attempts. Default is 1.</li>
 * <li><i>delay</i> (required) - a Long for the delay between attempts.</li>
 * <li><i>unit</i> (optional) - a String with the value of one of 'msec', 'sec', 'minute', 'hour', or 'day'. Default is msec.</li>
 * </ul>
 * Default is one attempt after 6 hours, which if explicitly declared would be written as <code>&lt;delayTime&gt;1 * 6 hour&lt;/delayTime&gt;</code></li>
 * <li><b>maxRetries</b> (optional) an Integer for the number of times an attempt is made to deliver a particular mail.
 * Default is the greater of five and the sum of the attempts for each <code>&lt;delayTime/&gt;</code> specified.
 * <li><b>maxDnsProblemRetries</b> (optional) - an Integer for the number of times to retry if DNS problems for a domain occur.
 * Default is 0.
 * <li><b>timeout</b> (optional) - an Integer for the Socket I/O timeout in milliseconds. Default is 180000</li>
 * <li><b>connectionTimeout</b> (optional) - an Integer for the Socket connection timeout in milliseconds. Default is 60000</li>
 * <li><b>bounceProcessor</b> (optional) - a String containing the name of the mailet processor to pass messages that cannot
 * be delivered to for DSN bounce processing. Default is to send a traditional message containing the bounce details.</li>
 * <li><b>onSuccess</b> (optional) - if specified, this processor is called for each email successfully sent to remote third parties.</li>
 *
 * When using bounceProcessor or onSuccess processors, take special care of error handling (see onMailetException and onMatcherException)
 * to avoid confusing situations. Also remember that on partial delivery, both processors will be used: <code>onSuccess</code> with successfull recipients,
 * and <code>bounceProcessor</code> with failed recipients.
 *
 * <li><b>startTLS</b> (optional) - a Boolean (true/false) indicating whether the STARTTLS command (if supported by the server)
 * to switch the connection to a TLS-protected connection before issuing any login commands. Default is false.</li>
 * <li><b>sslEnable</b> (optional) - a Boolean (true/false) indicating whether to use SSL to connect and use the SSL port unless
 * explicitly overridden. Default is false. The trust-store if needed can be customized by
 * <strong>-Djavax.net.ssl.trustStore=/root/conf/keystore</strong>.</li>
 * <li><b>verifyServerIdentity</b> (optional) - a Boolean (true/false) indicating whether to match the remote server name against its
 * certificate on TLS connections. Default is true. Disabling this runs the risk of someone spoofing a legitimate server and intercepting
 * mails, but may be necessary to contact servers that have strange certificates, no DNS entries, are reachable by IP address only,
 * and similar edge cases.</li>
 * <li><b>gateway</b> (optional) - a String containing a comma separated list of patterns defining the gateway servers to be used to
 * deliver mail regardless of the recipient address. If multiple gateway servers are defined, each will be tried in definition order
 * until delivery is successful. If none are successful, the mail is bounced. The pattern is <code>host[:port]</code> where:
 * <ul>
 * <li><i>host</i> (required) - the FQN of the gateway server.</li>
 * <li><i>port</i> (optional) - the port of the gateway server. Default is the value defined in the <code>&lt;gatewayPort/&gt;</code>
 * parameter if set, else the default port for the specified connection type.</li>
 * </ul>
 * Default is to resolve the destination SMTP server for each mail using MX lookup.
 * </li>
 * <li><b>gatewayPort</b> (optional) - an Integer for the gateway port to be used for each defined gateway server for which a
 * port is not explicitly defined in the <code>&lt;gateway/&gt;</code> parameter. Default is the default port for the specified connection type.</li>
 * <li><b>gatewayUsername</b> (optional) - a String containing the user name to be used to authenticate the user using the
 * AUTH command. Default is not to issue the AUTH command.
 * <li><b>gatewayPassword</b> (required if <code>gatewayUsername</code>) is set - a String representing the password to be used
 * to authenticate the user using the AUTH command.
 * <li><b>heloName</b> (optional) - a String containing the name used in the SMTP HELO and EHLO commands. Default is the default domain,
 * which is typically <code>localhost</code>.</li>
 * <li><b>mail.*</b> (optional) - Any property beginning with <code>mail.</code> described in the Javadoc for package
 * <a href="https://eclipse-ee4j.github.io/angus-mail/docs/api/org.eclipse.angus.mail/org/eclipse/angus/mail/smtp/package-summary.html"><code>org.eclipse.angus.mail.smtp</code></a>
 * can be set with a parameter of the corresponding name. For example the parameter
 * <code>&lt;mail.smtp.ssl.enable&gt;true&lt;/mail.smtp.ssl.enable&gt;</code> is equivalent to the Java code
 * <code>props.put("mail.smtp.ssl.enable", "true");</code>. Properties set by this facility override settings made
 * within the mailet code.<br>
 * Note: This facility should be used with extreme care by expert users with a thorough knowledge of the relevant RFCs and
 * the ability to perform their own problem resolutions.</li>
 * <li><b>debug</b> (optional) - a Boolean (true/false) indicating whether debugging is on. Default is false.</li>
 * </ul>
 * <br/>
 * <b>Security:</b><br/>
 * You can use the <b>sslEnable</b> parameter described above to force SMTP outgoing delivery to default to SSL encrypted traffic (SMTPS).
 * This is a shortcut for the <i>mail.smtps.ssl.enable</i> javax property.<br/>
 * When enabling SSL, you might need to specify the <i>mail.smtps.ssl.trust</i> property as well.
 * You can also control ciphersuites and protocols via *mail.smtps.ssl.ciphersuites* and 
 * <b>mail.smtps.ssl.protocols</b> properties.<br/>
 * StartTLS can alternatively be enabled upon sending a mail. For this, use the <b>startTls</b> parameter, serving as a shortcut for the
 * javax <i>mail.smtp.starttls.enable</i> property. Depending on how strict your security policy is, you might consider
 * <i>mail.smtp.starttls.required</i> as well. Be aware that configuring trust will then be required.
 * You can also use other javax properties for StartTLS, but their property prefix must be <i>mail.smtp.ssl.</i> in this case.<br/> 
 * James enables server identity verification by default. In certain rare edge cases you might disable it via the <b>verifyServerIdentity</b> parameter,
 * or use the <i>mail.smtps.ssl.checkserveridentity</i> and <i>mail.smtp.ssl.checkserveridentity</i> javax properties for fine control.<br/>
 * Read <a href="https://eclipse-ee4j.github.io/angus-mail/docs/api/org.eclipse.angus.mail/org/eclipse/angus/mail/smtp/package-summary.html"><code>org.eclipse.angus.mail.smtp</code></a>
 * for full information.
 */
public class RemoteDelivery extends GenericMailet {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteDelivery.class);
    private DeliveryRunnable deliveryRunnable;

    public enum ThreadState {
        START_THREADS,
        DO_NOT_START_THREADS
    }

    public static final String NAME_JUNCTION = "-to-";

    private final DNSService dnsServer;
    private final DomainList domainList;
    private final MailQueueFactory<?> queueFactory;
    private final MetricFactory metricFactory;
    private final ThreadState startThreads;

    private MailQueue queue;
    private RemoteDeliveryConfiguration configuration;

    @Inject
    public RemoteDelivery(DNSService dnsServer, DomainList domainList, MailQueueFactory<?> queueFactory, MetricFactory metricFactory) {
        this(dnsServer, domainList, queueFactory, metricFactory, ThreadState.START_THREADS);
    }

    public RemoteDelivery(DNSService dnsServer, DomainList domainList, MailQueueFactory<?> queueFactory, MetricFactory metricFactory, ThreadState startThreads) {
        this.dnsServer = dnsServer;
        this.domainList = domainList;
        this.queueFactory = queueFactory;
        this.metricFactory = metricFactory;
        this.startThreads = startThreads;
    }

    @Override
    public void init() throws MessagingException {
        configuration = new RemoteDeliveryConfiguration(getMailetConfig(), domainList);
        queue = queueFactory.createQueue(configuration.getOutGoingQueueName());
        deliveryRunnable = new DeliveryRunnable(queue,
            configuration,
            dnsServer,
            metricFactory,
            getMailetContext(),
            new Bouncer(configuration, getMailetContext()));
        if (startThreads == ThreadState.START_THREADS) {
            deliveryRunnable.start();
        }
    }

    @Override
    public String getMailetInfo() {
        return "RemoteDelivery Mailet";
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        if (configuration.isDebug()) {
            LOGGER.debug("Remotely delivering mail {}", mail.getName());
        }
        if (configuration.isUsePriority()) {
            mail.setAttribute(MailPrioritySupport.HIGH_PRIORITY_ATTRIBUTE);
        }
        if (!mail.getRecipients().isEmpty()) {
            if (configuration.getGatewayServer().isEmpty()) {
                serviceNoGateway(mail);

                AuditTrail.entry()
                    .protocol("mailetcontainer")
                    .action("RemoteDelivery")
                    .parameters(Throwing.supplier(() -> ImmutableMap.of("mailId", mail.getName(),
                        "mimeMessageId", Optional.ofNullable(mail.getMessage())
                            .map(Throwing.function(MimeMessage::getMessageID))
                            .orElse(""),
                        "sender", mail.getMaybeSender().asString(),
                        "recipients", StringUtils.join(mail.getRecipients()))))
                    .log("Remote delivering mail planned without gateway.");
            } else {
                serviceWithGateway(mail);

                AuditTrail.entry()
                    .protocol("mailetcontainer")
                    .action("RemoteDelivery")
                    .parameters(Throwing.supplier(() -> ImmutableMap.of("gateways", StringUtils.join(configuration.getGatewayServer()),
                        "mailId", mail.getName(),
                        "mimeMessageId", Optional.ofNullable(mail.getMessage())
                            .map(Throwing.function(MimeMessage::getMessageID))
                            .orElse(""),
                        "sender", mail.getMaybeSender().asString(),
                        "recipients", StringUtils.join(mail.getRecipients()))))
                    .log("Remote delivering mail planned with gateway.");
            }
        } else {
            LOGGER.debug("Mail {} from {} has no recipients and can not be remotely delivered", mail.getName(), mail.getMaybeSender());
        }
        mail.setState(Mail.GHOST);
    }

    private void serviceWithGateway(Mail mail) throws MailQueueException {
        if (configuration.isDebug()) {
            LOGGER.debug("Sending mail to {} via {}", mail.getRecipients(), configuration.getGatewayServer());
        }
        queue.enQueue(mail);
    }

    private void serviceNoGateway(Mail mail) throws MailQueueException {
        String mailName = mail.getName();
        Map<Domain, Collection<MailAddress>> targets = groupByServer(mail.getRecipients());
        for (Map.Entry<Domain, Collection<MailAddress>> entry : targets.entrySet()) {
            serviceSingleServer(mail, mailName, entry);
        }
    }

    private void serviceSingleServer(Mail mail, String originalName, Map.Entry<Domain, Collection<MailAddress>> entry) throws MailQueueException {
        if (configuration.isDebug()) {
            LOGGER.debug("Sending mail to {} on host {}", entry.getValue(), entry.getKey());
        }
        mail.setRecipients(entry.getValue());
        mail.setName(originalName + NAME_JUNCTION + entry.getKey().name());

        queue.enQueue(mail);
    }

    private Map<Domain, Collection<MailAddress>> groupByServer(Collection<MailAddress> recipients) {
        // Must first organize the recipients into distinct servers (name made case insensitive)
        HashMultimap<Domain, MailAddress> groupByServerMultimap = HashMultimap.create();
        for (MailAddress recipient : recipients) {
            groupByServerMultimap.put(recipient.getDomain(), recipient);
        }
        return groupByServerMultimap.asMap();
    }

    /**
     * Stops all the worker threads that are waiting for messages. This method
     * is called by the Mailet container before taking this Mailet out of
     * service.
     */
    @Override
    public void destroy() {
        if (startThreads == ThreadState.START_THREADS) {
            deliveryRunnable.dispose();
        }
        try {
            queue.close();
        } catch (IOException e) {
            LOGGER.debug("error closing queue", e);
        }
    }

    @Override
    public Collection<ProcessingState> requiredProcessingState() {
        return Stream.of(configuration.getBounceProcessor().stream(),
                configuration.getOnSuccess().stream())
            .flatMap(x -> x)
            .collect(ImmutableList.toImmutableList());
    }
}
