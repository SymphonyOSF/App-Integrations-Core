package org.symphonyoss.integration.web.resource;

import static org.mockito.Mockito.when;

import com.symphony.api.pod.model.ConfigurationInstance;
import com.symphony.api.pod.model.V1Configuration;

import org.mockito.Mock;
import org.symphonyoss.integration.IntegrationStatus;
import org.symphonyoss.integration.service.ConfigurationService;
import org.symphonyoss.integration.exception.config.IntegrationConfigException;
import org.symphonyoss.integration.service.IntegrationBridge;
import org.symphonyoss.integration.healthcheck.IntegrationBridgeHealthManager;
import org.symphonyoss.integration.model.healthcheck.IntegrationBridgeHealth;
import org.symphonyoss.integration.model.healthcheck.IntegrationBridgeHealthConnectivity;
import org.symphonyoss.integration.model.healthcheck.IntegrationHealth;
import org.symphonyoss.integration.webhook.WebHookIntegration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import javax.servlet.http.HttpServletRequest;

/**
 * Base class to support unit tests for web resources.
 *
 * Created by rsanchez on 19/10/16.
 */
public abstract class WebHookResourceTest {

  public static final String HEADER_NAME = "header";

  public static final String HEADER_VALUE = "headerValue";

  public static final String PARAM_NAME = "param";

  public static final String PARAM_VALUE = "paramValue";

  /**
   * jiraWebHookIntegration sha1 hash.
   */
  public static final String TEST_HASH = "c518e9ed5fd5f9492f75fba443d014061cd63042";

  /**
   * Configuration ID stub.
   */
  public static final String CONFIGURATION_ID = "57756bca4b54433738037005";

  /**
   * Test User stub.
   */
  public static final String TEST_USER = "jiraWebHookIntegration";

  @Mock(name = "configurationService")
  protected ConfigurationService configurationService;

  @Mock(name = "integrationBridge")
  protected IntegrationBridge integrationBridge;

  /**
   * Used within mocked services.
   */
  @Mock
  protected V1Configuration whiConfiguration;

  /**
   * Used within mocked services.
   */
  @Mock
  protected ConfigurationInstance whiInstance;

  /**
   * Used within mocked services.
   */
  @Mock
  protected WebHookIntegration whiIntegration;

  @Mock
  protected HttpServletRequest request;

  @Mock
  protected ScheduledExecutorService scheduler;

  @Mock
  private IntegrationBridgeHealthManager healthCheckManager;

  protected void mockHealthCheckManager() {
    IntegrationBridgeHealthConnectivity connectivity = new IntegrationBridgeHealthConnectivity();
    connectivity.setKm(IntegrationBridgeHealth.StatusEnum.UP.name());
    connectivity.setPod(IntegrationBridgeHealth.StatusEnum.UP.name());
    connectivity.setAgent(IntegrationBridgeHealth.StatusEnum.UP.name());
//    doReturn(connectivity).when(healthCheckManager).getConnectivityStatus();
  }

  protected void mockHealthCheckManagerKmDown() {
    IntegrationBridgeHealthConnectivity connectivity = new IntegrationBridgeHealthConnectivity();
    connectivity.setKm(IntegrationBridgeHealth.StatusEnum.DOWN.name());
    connectivity.setPod(IntegrationBridgeHealth.StatusEnum.UP.name());
    connectivity.setAgent(IntegrationBridgeHealth.StatusEnum.UP.name());
//    doReturn(connectivity).when(healthCheckManager).getConnectivityStatus();
  }

  protected void mockHealthCheckManagerPodDown() {
    IntegrationBridgeHealthConnectivity connectivity = new IntegrationBridgeHealthConnectivity();
    connectivity.setKm(IntegrationBridgeHealth.StatusEnum.UP.name());
    connectivity.setPod(IntegrationBridgeHealth.StatusEnum.DOWN.name());
    connectivity.setAgent(IntegrationBridgeHealth.StatusEnum.UP.name());
//    doReturn(connectivity).when(healthCheckManager).getConnectivityStatus();
  }

  protected void mockHealthCheckManagerAgentDown() {
    IntegrationBridgeHealthConnectivity connectivity = new IntegrationBridgeHealthConnectivity();
    connectivity.setKm(IntegrationBridgeHealth.StatusEnum.UP.name());
    connectivity.setPod(IntegrationBridgeHealth.StatusEnum.UP.name());
    connectivity.setAgent(IntegrationBridgeHealth.StatusEnum.DOWN.name());
//    doReturn(connectivity).when(healthCheckManager).getConnectivityStatus();
  }

  protected void mockStatus(IntegrationStatus status) {
    IntegrationHealth integrationHealth = new IntegrationHealth();
    integrationHealth.setStatus(status.name());
    integrationHealth.setMessage(status.toString());

    when(whiIntegration.getHealthStatus()).thenReturn(integrationHealth);
  }

  protected void mockRequest() {
    // mocking httpRequest
    List<String> paramNames = new ArrayList<>();
    paramNames.add(PARAM_NAME);
    Enumeration paramEnum = Collections.enumeration(paramNames);

    when(request.getParameterNames()).thenReturn(paramEnum);
    when(request.getParameter(PARAM_NAME)).thenReturn(PARAM_VALUE);

    List<String> headerNames = new ArrayList<>();
    headerNames.add(HEADER_NAME);
    Enumeration headerEnum = Collections.enumeration(headerNames);

    when(request.getHeaderNames()).thenReturn(headerEnum);
    when(request.getHeader(HEADER_NAME)).thenReturn(HEADER_VALUE);
  }

  protected void mockConfiguration(boolean enabled) throws IntegrationConfigException {
    // mocking configuration
    when(whiConfiguration.getConfigurationId()).thenReturn(CONFIGURATION_ID);
    when(whiConfiguration.getEnabled()).thenReturn(enabled);

    // mocking configurationInstance
    when(whiInstance.getConfigurationId()).thenReturn(CONFIGURATION_ID);

    // mocking configuration service
    when(configurationService.getInstanceById(CONFIGURATION_ID, TEST_HASH, TEST_USER)).thenReturn(
        whiInstance);
    when(configurationService.getConfigurationById(CONFIGURATION_ID, TEST_USER)).thenReturn(
        whiConfiguration);

    // mocking integration bridge
    when(integrationBridge.getIntegrationById(CONFIGURATION_ID)).thenReturn(whiIntegration);
  }
}
