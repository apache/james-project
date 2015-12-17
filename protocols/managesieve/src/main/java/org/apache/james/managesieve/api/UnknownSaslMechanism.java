

package org.apache.james.managesieve.api;

public class UnknownSaslMechanism extends ManageSieveException {

    public UnknownSaslMechanism(String unknownMechanism) {
        super("Unknown SASL mechanism " + unknownMechanism);
    }
}
