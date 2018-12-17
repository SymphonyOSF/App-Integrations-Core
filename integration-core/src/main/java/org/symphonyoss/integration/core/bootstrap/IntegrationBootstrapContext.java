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

package org.symphonyoss.integration.core.bootstrap;

import static org.symphonyoss.integration.core.properties.IntegrationBootstrapContextProperties
    .FAIL_BOOTSTRAP_INTEGRATION;
import static org.symphonyoss.integration.core.properties.IntegrationBootstrapContextProperties
    .FAIL_BOOTSTRAP_INTEGRATION_RETRYING;
import static org.symphonyoss.integration.core.properties.IntegrationBootstrapContextProperties
    .INTEGRATION_SUCCESSFULLY_BOOTSTRAPPED;
import static org.symphonyoss.integration.core.properties.IntegrationBootstrapContextProperties
    .NO_INTEGRATION_FOR_BOOTSTRAP;
import static org.symphonyoss.integration.core.properties.IntegrationBootstrapContextProperties
    .POLLING_STOPPED;
import static org.symphonyoss.integration.core.properties.IntegrationBootstrapContextProperties
    .POLLING_STOPPED_SOLUTION;
import static org.symphonyoss.integration.core.properties.IntegrationBootstrapContextProperties
    .SHUTTING_DOWN_INTEGRATION;
import static org.symphonyoss.integration.core.properties.IntegrationBootstrapContextProperties
    .VERIFY_NEW_INTEGRATIONS;
import static org.symphonyoss.integration.logging.DistributedTracingUtils.TRACE_ID;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.symphonyoss.integration.Integration;
import org.symphonyoss.integration.authentication.AuthenticationProxy;
import org.symphonyoss.integration.core.NullIntegration;
import org.symphonyoss.integration.core.runnable.IntegrationAbstractRunnable;
import org.symphonyoss.integration.exception.IntegrationRuntimeException;
import org.symphonyoss.integration.exception.authentication.ConnectivityException;
import org.symphonyoss.integration.exception.bootstrap.RetryLifecycleException;
import org.symphonyoss.integration.healthcheck.application.ApplicationsHealthIndicator;
import org.symphonyoss.integration.logging.DistributedTracingUtils;
import org.symphonyoss.integration.logging.LogMessageSource;
import org.symphonyoss.integration.metrics.IntegrationMetricsController;
import org.symphonyoss.integration.model.config.IntegrationSettings;
import org.symphonyoss.integration.model.yaml.Application;
import org.symphonyoss.integration.model.yaml.ApplicationState;
import org.symphonyoss.integration.model.yaml.IntegrationProperties;
import org.symphonyoss.integration.utils.IntegrationUtils;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bootstraps all {@link Integration} that exists on the Spring context.
 *
 * Created by Milton Quilzini on 04/05/16.
 */
@Component
public class IntegrationBootstrapContext implements IntegrationBootstrap {

  private static final Logger LOGGER = LoggerFactory.getLogger(IntegrationBootstrapContext.class);

  private static final int MAX_RETRY_ATTEMPTS_FOR_LIFECYCLE_EXCEPTION = 5;

  public static final Integer DEFAULT_POOL_SIZE = 10;

  public static final String INITAL_DELAY = "50";

  public static final String DEFAULT_DELAY = "60000";

  public static final String BOOTSTRAP_INITIAL_DELAY_KEY = "bootstrap.init.delay";

  public static final String BOOTSTRAP_DELAY_KEY = "bootstrap.delay";

  @Autowired
  private ApplicationContext context;

  @Autowired
  private IntegrationProperties properties;

  @Autowired
  private AuthenticationProxy authenticationProxy;

  private Map<String, Integration> integrations = new ConcurrentHashMap<>();

  private BlockingQueue<IntegrationBootstrapInfo> integrationsToRegister =
      new LinkedTransferQueue<>();

  private ExecutorService servicePool;

  private ScheduledExecutorService scheduler;

  @Autowired
  protected IntegrationUtils utils;

  @Autowired
  private IntegrationMetricsController metricsController;

  @Autowired
  private ApplicationsHealthIndicator applicationsHealthIndicator;

  @Autowired
  private ApplicationEventPublisher publisher;

  @Autowired
  private IntegrationLogging logging;

  @Autowired
  private LogMessageSource logMessage;

  @Autowired
  private Environment environment;

  /**
   * Atomic  Integer used to control when the application should log its health.
   * The application health should only be logged after the last default integration finishes
   * to try its bootstrap process. After new integrations are added and try to bootstrap the
   * health should also be logged.
   */
  private AtomicInteger logHealthApplicationCounter = new AtomicInteger();

  @Override
  public void startup() {
    DistributedTracingUtils.setMDC();
    this.scheduler = Executors.newScheduledThreadPool(DEFAULT_POOL_SIZE);
    this.servicePool = Executors.newFixedThreadPool(DEFAULT_POOL_SIZE);

    initIntegrations();

  }

  /**
   * Initialize deployed integrations
   */

  public void initIntegrations() {
    Map<String, Integration> integrations = this.context.getBeansOfType(Integration.class);

    if (integrations == null || integrations.isEmpty()) {
      LOGGER.warn(logMessage.getMessage(NO_INTEGRATION_FOR_BOOTSTRAP));
    } else {
      // Integration components
      for (String configurationType : integrations.keySet()) {
        Integration integration = integrations.get(configurationType);
        IntegrationBootstrapInfo info =
            new IntegrationBootstrapInfo(configurationType, integration);
        try {
          integrationsToRegister.offer(info, 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          LOGGER.warn("It's not possible to add the Integration.");
        }
      }

      String delay = System.getProperty(BOOTSTRAP_DELAY_KEY, DEFAULT_DELAY);
      String initialDelay = System.getProperty(BOOTSTRAP_INITIAL_DELAY_KEY, INITAL_DELAY);
      scheduleHandleIntegrations(Long.valueOf(initialDelay), Long.valueOf(delay),
          TimeUnit.MILLISECONDS);
      // deals with unknown apps.
      initUnknownApps();

    }

  }

  /**
   * Applications that were found in the properties file but don't have a corresponding bean on
   * their own
   * are considered "Unknown Applications".
   * They will show up on health checks for the Integration Bridge as non-ACTIVE applications as
   * they aren't actually implemented.
   * This is more likely to happen if someone configures the provisioning YAML file with an
   * incorrect application name or with an integration name that does not actually exist for the
   * time being.
   */
  private void initUnknownApps() {
    Map<String, Application> applications = properties.getApplications();
    for (Map.Entry<String, Application> entry : applications.entrySet()) {
      Application application = entry.getValue();

      if ((StringUtils.isEmpty(application.getComponent())) && (ApplicationState.PROVISIONED.equals(
          application.getState()))) {
        String appId = entry.getKey();
        application.setId(appId);

        NullIntegration integration =
            new NullIntegration(applicationsHealthIndicator, application, utils,
                authenticationProxy, logMessage, environment);

        try {
          integration.onCreate(appId);
        } catch (IntegrationRuntimeException e) {
          LOGGER.error(e.getMessage(), appId);
        }
      }
    }
  }

  /**
   * Schedule a new thread to handle new integrations.
   * @param initialDelay to start looking for new integrations.
   * @param delay time interval for each check.
   * @param unit the {@link TimeUnit} for the other 2 parameters.
   */
  private void scheduleHandleIntegrations(long initialDelay, long delay, TimeUnit unit) {
    scheduler.scheduleAtFixedRate(new IntegrationAbstractRunnable(MDC.get(TRACE_ID)) {
      @Override
      protected void execute() {
        handleIntegrations();
      }
    }, initialDelay, delay, unit);
  }

  /**
   * Handle integrations that for some reason failed to bootstrap correctly.
   * It will try to bootstrap any integrations registered under our queue {@link BlockingQueue}.
   * Common reasons for an integration to be on this "retry state" are connectivity problems or
   * faulty configurations.
   */
  private void handleIntegrations() {
    try {
      LOGGER.debug(logMessage.getMessage(VERIFY_NEW_INTEGRATIONS));

      // Sets the new application health check counter
      this.logHealthApplicationCounter.set(integrationsToRegister.size());

      while (!integrationsToRegister.isEmpty()) {
        IntegrationBootstrapInfo info = integrationsToRegister.poll(5, TimeUnit.SECONDS);

        if (info != null) {
          Application application = properties.getApplication(info.getConfigurationType());

          if ((application != null) && (ApplicationState.PROVISIONED.equals(
              application.getState()))) {
            submitPoolTask(info);
          }
        }
      }
    } catch (InterruptedException e) {
      LOGGER.error(logMessage.getMessage(POLLING_STOPPED), e, POLLING_STOPPED_SOLUTION);
    }
  }

  private void submitPoolTask(final IntegrationBootstrapInfo info) {
    this.servicePool.submit(new IntegrationAbstractRunnable(MDC.get(TRACE_ID)) {
      @Override
      protected void execute() {
        setupIntegration(info);
      }
    });
  }

  /**
   * Logs the application health, however the logging should only happen on these occasions: after
   * the first
   * integration finishes its bootstrap process, after new integrations are added or after an
   * exception
   * happens when trying to bootstrap an integration.
   */
  public void logHealthCheck() {
    if (logHealthApplicationCounter.decrementAndGet() == 0) {
      logging.logHealth();
    }
  }

  /**
   * Perform the integration setup
   * @param info
   */
  private void setupIntegration(IntegrationBootstrapInfo info) {
    String integrationUser = info.getConfigurationType();
    Integration integration = info.getIntegration();

    try {
      integration.onCreate(integrationUser);

      IntegrationSettings settings = integration.getSettings();
      this.integrations.put(settings.getConfigurationId(), integration);

      metricsController.addIntegrationTimer(integrationUser);

      LOGGER.info(logMessage.getMessage(INTEGRATION_SUCCESSFULLY_BOOTSTRAPPED, integrationUser));

      logging.logIntegration(integration);
    } catch (ConnectivityException e) {
      LOGGER.error(logMessage.getMessage(FAIL_BOOTSTRAP_INTEGRATION_RETRYING, integrationUser), e);
      try {
        integrationsToRegister.offer(info, 10, TimeUnit.SECONDS);
      } catch (InterruptedException e1) {
        LOGGER.warn("It's not possible to add the Integration.");
      }
    } catch (RetryLifecycleException e) {
      checkRetryAttempt(info, e);
    } catch (IntegrationRuntimeException e) {
      LOGGER.error(logMessage.getMessage(FAIL_BOOTSTRAP_INTEGRATION, integrationUser), e);
    } finally {
      logHealthCheck();
    }
  }

  private void checkRetryAttempt(IntegrationBootstrapInfo integrationInfo,
      RetryLifecycleException e) {
    int retryAttempts = integrationInfo.registerRetryAttempt();
    if (retryAttempts <= MAX_RETRY_ATTEMPTS_FOR_LIFECYCLE_EXCEPTION) {
      LOGGER.error(logMessage.getMessage(FAIL_BOOTSTRAP_INTEGRATION_RETRYING,
          integrationInfo.getConfigurationType()), e);
      try {
        integrationsToRegister.offer(integrationInfo,10, TimeUnit.SECONDS);
      } catch (InterruptedException e1) {
        LOGGER.warn("It's not possible to add the Integration.");
      }
    } else {
      LOGGER.error(logMessage.getMessage(FAIL_BOOTSTRAP_INTEGRATION,
          integrationInfo.getConfigurationType()), e);
    }
  }

  @Override
  public void shutdown() throws IllegalStateException {
    destroyIntegrations();

    this.scheduler.shutdown();
    this.servicePool.shutdown();
  }

  private void destroyIntegrations() {
    for (Integration integration : this.integrations.values()) {
      LOGGER.info(
          logMessage.getMessage(SHUTTING_DOWN_INTEGRATION, integration.getClass().getSimpleName()));
      integration.onDestroy();
    }

    this.integrations.clear();
  }

  @Override
  public Integration getIntegrationById(String id) throws IllegalStateException {
    return this.integrations.get(id);
  }

  @Override
  public void removeIntegration(String id) {
    Integration integration = getIntegrationById(id);

    if (integration != null) {
      this.integrations.remove(id);
    }
  }

}