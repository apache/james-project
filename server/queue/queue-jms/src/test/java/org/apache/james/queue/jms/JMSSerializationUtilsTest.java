package org.apache.james.queue.jms;

import static org.apache.james.queue.jms.JMSSerializationUtils.deserialize;
import static org.apache.james.queue.jms.JMSSerializationUtils.hasJMSNativeSupport;
import static org.apache.james.queue.jms.JMSSerializationUtils.roundtrip;
import static org.apache.james.queue.jms.JMSSerializationUtils.trySerialize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.Serializable;
import java.util.Objects;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.SerializationException;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

class JMSSerializationUtilsTest {
    @Test
    @SuppressWarnings("ConstantConditions")
    void hasJMSNativeSupportSpec() {
        SoftAssertions softly = new SoftAssertions();

        softly.assertThat(hasJMSNativeSupport(null)).as("null support").isTrue();
        softly.assertThat(hasJMSNativeSupport("")).as("String support").isTrue();
        softly.assertThat(hasJMSNativeSupport((byte) 0)).as("Byte support").isTrue();
        softly.assertThat(hasJMSNativeSupport((long) 0)).as("Long support").isTrue();
        softly.assertThat(hasJMSNativeSupport((double) 0)).as("Double support").isTrue();
        softly.assertThat(hasJMSNativeSupport(0)).as("Integer support").isTrue();
        softly.assertThat(hasJMSNativeSupport((short) 0)).as("Short support").isTrue();
        softly.assertThat(hasJMSNativeSupport((float) 0)).as("Float support").isTrue();
        softly.assertThat(hasJMSNativeSupport(true)).as("Boolean support").isTrue();

        softly.assertAll();
    }

    @Test
    void trySerializeShouldReturnItselfWhenJMSSupported() {
        Integer expected = 41;

        Object actual = trySerialize(expected);

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void trySerializeShouldReturnString() {
        SerializableStringHolder value = new SerializableStringHolder("value");

        Object actual = trySerialize(value);

        assertThat(actual)
                .isInstanceOf(String.class);
    }

    @Test
    void roundTripShouldReturnEqualObject() {
        SerializableStringHolder expected = new SerializableStringHolder("value");

        assertThat(roundtrip(expected))
                .isPresent()
                .hasValue(expected);
    }

    @Test
    void deserializeShouldThrowWhenNotBase64StringProvided() {
        assertThatExceptionOfType(SerializationException.class)
                .isThrownBy(() -> deserialize("abc"));
    }

    @Test
    void deserializeShouldThrowWhenNotSerializedBytesAreEncodedInBase64() {
        assertThatExceptionOfType(SerializationException.class)
                .isThrownBy(() -> deserialize(Base64.encodeBase64String("abc".getBytes())));
    }

    private static class SerializableStringHolder implements Serializable {
        private final String value;

        SerializableStringHolder(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SerializableStringHolder)) return false;
            SerializableStringHolder that = (SerializableStringHolder) o;
            return Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {

            return Objects.hash(value);
        }
    }
}