package org.apache.james.backends.pulsar

import java.net.{URI, URISyntaxException}
import org.apache.commons.configuration2.Configuration
import com.google.common.base.Strings

object PulsarConfiguration {
  val BROKER_URI_PROPERTY_NAME = "broker.uri"
  val ADMIN_URI_PROPERTY_NAME = "admin.uri"
  val NAMESPACE_PROPERTY_NAME = "namespace"

  def from(configuration: Configuration): PulsarConfiguration = {
    val brokerUri: String = extractUri(configuration, BROKER_URI_PROPERTY_NAME)
    val adminUri: String = extractUri(configuration, ADMIN_URI_PROPERTY_NAME)


    val namespace = configuration.getString(NAMESPACE_PROPERTY_NAME)
    if (Strings.isNullOrEmpty(namespace))
      throw new IllegalStateException("You need to specify the pulsar namespace as " + NAMESPACE_PROPERTY_NAME)
    new PulsarConfiguration(brokerUri, adminUri, Namespace(namespace))
  }

  private def extractUri(configuration: Configuration, uriPropertyName: String): String = {
    val extractedUri = configuration.getString(uriPropertyName)
    if (Strings.isNullOrEmpty(extractedUri))
      throw new IllegalStateException("You need to specify the pulsar "+uriPropertyName+" uri")
    try {
      new URI(extractedUri)
    } catch {
      case ex: URISyntaxException =>
        throw new IllegalStateException(s"'$extractedUri' is not a valid $uriPropertyName uri", ex)
    }
    extractedUri
  }
}

case class Namespace(asString: String)

case class PulsarConfiguration(brokerUri: String, adminUri: String, namespace: Namespace)