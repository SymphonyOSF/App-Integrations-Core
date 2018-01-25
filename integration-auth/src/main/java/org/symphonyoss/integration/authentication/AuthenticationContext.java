/**
 * Copyright 2016-2017 Symphony Integrations - Symphony LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.symphonyoss.integration.authentication;

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import org.glassfish.jersey.SslConfigurator;
import org.glassfish.jersey.apache.connector.ApacheClientProperties;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.symphonyoss.integration.authentication.api.enums.ServiceName;
import org.symphonyoss.integration.authentication.exception.MissingClientException;
import org.symphonyoss.integration.model.yaml.ConnectionInfo;
import org.symphonyoss.integration.model.yaml.HttpClientConfig;
import org.symphonyoss.integration.model.yaml.IntegrationProperties;
import org.symphonyoss.integration.model.yaml.ProxyConnectionInfo;

import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

/**
 * Abstract class to support context management for an integration.
 *
 * Created by ecarrenho on 8/23/16.
 */
public abstract class AuthenticationContext {

  private final Map<ServiceName, Client> serviceClients = new HashMap<>();

  /**
   * Initializes HTTP client with the SSL Context according to the keystore received.
   * @param keyStore Keystore object
   * @param keyStorePassword Keystore password
   * @param httpClientConfig API client settings
   */
  public AuthenticationContext(KeyStore keyStore, String keyStorePassword, HttpClientConfig
      httpClientConfig, IntegrationProperties properties) {
    if (httpClientConfig == null) {
      httpClientConfig = new HttpClientConfig();
    }

    Map<ServiceName, ConnectionInfo> services = properties.getServices();

    for (Map.Entry<ServiceName, ConnectionInfo> entry : services.entrySet()) {
      ServiceName service = entry.getKey();
      ConnectionInfo serviceInfo = entry.getValue();

      this.serviceClients.put(service,
          buildClient(keyStore, keyStorePassword, httpClientConfig, serviceInfo.getProxy()));
    }

  }

  /**
   * Builds HTTP client with the SSL Context according to the keystore received.
   * @param keyStore Keystore object
   * @param keyStorePassword Keystore password
   * @param httpClientConfig API client settings
   * @return HTTP client
   */
  private Client buildClient(KeyStore keyStore, String keyStorePassword,
      HttpClientConfig httpClientConfig, ProxyConnectionInfo proxyConnectionInfo) {
    final ClientConfig clientConfig = new ClientConfig();
    clientConfig.register(MultiPartFeature.class);

    // Connect and read timeouts in milliseconds
    clientConfig.property(ClientProperties.READ_TIMEOUT, httpClientConfig.getReadTimeout());
    clientConfig.property(ClientProperties.CONNECT_TIMEOUT, httpClientConfig.getConnectTimeout());

    // Socket factory setup with custom SSL context settings
    SSLConnectionSocketFactory sslSocketFactory;

    if (keyStore == null || keyStorePassword == null) {
      sslSocketFactory = SSLConnectionSocketFactory.getSystemSocketFactory();
    } else {
      SslConfigurator sslConfigurator = SslConfigurator.newInstance()
          .keyStore(keyStore)
          .keyStorePassword(keyStorePassword);

      sslSocketFactory = new SSLConnectionSocketFactory(sslConfigurator.createSSLContext());
    }

    Registry<ConnectionSocketFactory> socketFactoryRegistry =
        RegistryBuilder.<ConnectionSocketFactory>create()
            .register("http", PlainConnectionSocketFactory.getSocketFactory())
            .register("https", sslSocketFactory)
            .build();

    // Connection pool setup with custom socket factory and max connections
    PoolingHttpClientConnectionManager connectionManager =
        new PoolingHttpClientConnectionManager(socketFactoryRegistry);
    connectionManager.setMaxTotal(httpClientConfig.getMaxConnections());
    connectionManager.setDefaultMaxPerRoute(httpClientConfig.getMaxConnectionsPerRoute());

    // Sets the connector provider and connection manager (as shared to avoid the client runtime
    // to shut it down)
    clientConfig.property(ApacheClientProperties.CONNECTION_MANAGER, connectionManager);
    clientConfig.property(ApacheClientProperties.CONNECTION_MANAGER_SHARED, true);
    ApacheConnectorProvider connectorProvider = new ApacheConnectorProvider();
    clientConfig.connectorProvider(connectorProvider);

    // Adds proxy info if there is any
    if (proxyConnectionInfo != null) {

      String uri = proxyConnectionInfo.getURI();
      String user = proxyConnectionInfo.getUser();
      String password = proxyConnectionInfo.getPassword();

      if (uri != null) {
        clientConfig.property(ClientProperties.PROXY_URI, uri);
      }

      if (user != null) {
        clientConfig.property(ClientProperties.PROXY_USERNAME, user);
      }

      if (password != null) {
        clientConfig.property(ClientProperties.PROXY_PASSWORD, password);
      }
    }

    // Build the client with the above configurations
    final ClientBuilder clientBuilder = ClientBuilder.newBuilder().withConfig(clientConfig);

    return clientBuilder.build();
  }

  /**
   * Get HTTP client
   * @return HTTP client
   */
  public Client httpClientForContext(ServiceName serviceName) {
    if (serviceClients.containsKey(serviceName)) {
      return serviceClients.get(serviceName);
    } else {
      throw new MissingClientException(serviceName);
    }
  }
}