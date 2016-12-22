package org.symphonyoss.integration.authentication;

import static com.symphony.atlas.config.SymphonyAtlas.KEY_AUTH_URL;
import static com.symphony.atlas.config.SymphonyAtlas.SESSION_AUTH_URL;

import com.symphony.api.auth.client.ApiException;
import com.symphony.api.auth.model.Token;
import com.symphony.atlas.IAtlas;
import com.symphony.logging.ISymphonyLogger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.symphonyoss.integration.IntegrationAtlas;
import org.symphonyoss.integration.authentication.exception.AuthUrlNotFoundException;
import org.symphonyoss.integration.authentication.metrics.ApiMetricsController;
import org.symphonyoss.integration.exception.authentication.ConnectivityException;
import org.symphonyoss.integration.exception.authentication.ForbiddenAuthException;
import org.symphonyoss.integration.authentication.exception.KeyManagerConnectivityException;
import org.symphonyoss.integration.authentication.exception.PodConnectivityException;
import org.symphonyoss.integration.exception.authentication.UnauthorizedUserException;
import org.symphonyoss.integration.exception.authentication.UnexpectedAuthException;
import org.symphonyoss.integration.authentication.exception.UnregisteredSessionTokenException;
import org.symphonyoss.integration.authentication.exception.UnregisteredUserAuthException;
import org.symphonyoss.integration.exception.RemoteApiException;
import org.symphonyoss.integration.logging.IntegrationBridgeCloudLoggerFactory;

import java.io.IOException;
import java.security.KeyStore;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.Response.Status;

/**
 * Perform the user authentication and keep the tokens for each configuration.
 *
 * Created by rsanchez on 06/05/16.
 */
@Component
public class AuthenticationProxyImpl implements AuthenticationProxy {

  private static final ISymphonyLogger LOG =
      IntegrationBridgeCloudLoggerFactory.getLogger(AuthenticationProxyImpl.class);

  private static final Long MAX_SESSION_TIME_MILLIS = TimeUnit.MINUTES.toMillis(3);

  @Autowired
  private IntegrationAtlas integrationAtlas;

  /**
   * SBE Authentication API Client
   */
  private AuthenticationApiDecorator sbeAuthApi;

  /**
   * Key Manager Authentication API Client
   */
  private AuthenticationApiDecorator keyManagerAuthApi;

  private Map<String, AuthenticationContext> authContexts = new ConcurrentHashMap<>();

  @Autowired
  private ApiMetricsController metricsController;

  /**
   * Initialize HTTP clients.
   */
  @PostConstruct
  public void init() {
    IAtlas atlas = integrationAtlas.getAtlas();

    String sbeUrl = getUrl(atlas, SESSION_AUTH_URL);
    AuthApiClientDecorator sbeClient = new AuthApiClientDecorator(this, metricsController);
    sbeClient.setBasePath(sbeUrl);

    String keyManagerUrl = getUrl(atlas, KEY_AUTH_URL);
    AuthApiClientDecorator keyManagerClient = new AuthApiClientDecorator(this, metricsController);
    keyManagerClient.setBasePath(keyManagerUrl);

    this.sbeAuthApi = new AuthenticationApiDecorator(sbeClient);
    this.keyManagerAuthApi = new AuthenticationApiDecorator(keyManagerClient);
  }

  private String getUrl(IAtlas atlas, String key) {
    String sbeUrl = atlas.get(key);

    if (sbeUrl == null || sbeUrl.trim().isEmpty()) {
      throw new AuthUrlNotFoundException(
          "Missing Atlas configuration for authentication URL on pod. Set '" + key
              + "' in Atlas configuration file.");
    }
    return sbeUrl;
  }

  /**
   * Perform the user authentication.
   * @param userId
   * @throws ApiException
   */
  @Override
  public void authenticate(String userId) throws ApiException {
    AuthenticationContext context = contextForUser(userId);

    if (!context.isAuthenticated()) {
      LOG.info("Authenticate {}", userId);
      Token sessionToken = authSessionApi(userId);
      Token keyManagerToken = authKeyManagerApi(userId);

      context.setToken(
          new AuthenticationToken(sessionToken.getToken(), keyManagerToken.getToken()));
    }

  }

  private Token authKeyManagerApi(String userId) throws ApiException {
    Token keyManagerToken;
    try {
      keyManagerToken = keyManagerAuthApi.v1AuthenticatePost(userId);
      LOG.info("Token {} successfully", keyManagerToken.getName());
    } catch (ProcessingException e) {
      if (IOException.class.isInstance(e.getCause())) {
        throw new KeyManagerConnectivityException(e);
      } else {
        throw e;
      }
    }
    return keyManagerToken;
  }

  private Token authSessionApi(String userId) throws ApiException {
    Token sessionToken;
    try {
      sessionToken = sbeAuthApi.v1AuthenticatePost(userId);
      LOG.info("Token {} successfully", sessionToken.getName());
    } catch (ProcessingException e) {
      if (IOException.class.isInstance(e.getCause())) {
        throw new PodConnectivityException(e);
      } else {
        throw e;
      }
    }
    return sessionToken;
  }

  /**
   * Makes sure the user passed to auth proxy has been registered before, to avoid hard to find
   * bugs.
   */
  private AuthenticationContext contextForUser(String userId) {
    AuthenticationContext context = this.authContexts.get(userId);

    if (context == null) {
      throw new UnregisteredUserAuthException(
          "Internal Integration Bridge error. Authentication invoked for unknown user - ID "
              + userId);
    }

    return context;
  }

  /**
   * Makes sure the session token passed to auth proxy has been registered before, to avoid hard to
   * find bugs.
   */
  private AuthenticationContext contextForSessionToken(String sessionToken) {
    for (AuthenticationContext context : this.authContexts.values()) {
      if (context.getToken().getSessionToken().equals(sessionToken)) {
        return context;
      }
    }

    for (AuthenticationContext context : this.authContexts.values()) {
      if (context.getPreviousToken().getSessionToken().equals(sessionToken)) {
        return context;
      }
    }

    throw new UnregisteredSessionTokenException(
        "Internal Integration Bridge error. Authentication invoked for unknown user - ID");
  }

  @Override
  public boolean isAuthenticated(String user) {
    return contextForUser(user).isAuthenticated();
  }

  /**
   * Invalidate user session token.
   * @param userId
   */
  @Override
  public void invalidate(String userId) {
    contextForUser(userId).invalidateAuthentication();
    LOG.info("Invalidate session to {}", userId);
  }

  /**
   * Retrieve the authentication token based on configuration identifier
   * @param configurationId
   * @return
   */
  @Override
  public AuthenticationToken getToken(String configurationId) {
    return contextForUser(configurationId).getToken();
  }

  /**
   * Retrieve the session token based on configuration identifier
   * @param configurationId
   * @return
   */
  @Override
  public String getSessionToken(String configurationId) {
    return contextForUser(configurationId).getToken().getSessionToken();
  }

  /**
   * If the provided exception is of type unauthorized, then authenticate again, else rethrow the
   * same exception
   * @param userId
   * @param code
   * @param e
   * @throws ApiException an authorization exception thrown on FAILURE to re-auth
   * @throws com.symphony.api.agent.client.ApiException the original exception
   */
  @Override
  public synchronized void reAuthOrThrow(String userId, int code, Exception e)
      throws RemoteApiException {
    if (validateResponseCode(Status.UNAUTHORIZED, code)) {
      if (shouldInvalidateSession(userId)) {
        invalidate(userId);
        try {
          authenticate(userId);
        } catch (ApiException e1) {
          checkAndThrowException(e1, userId);
        } catch (ConnectivityException e2) {
          throw e2;
        } catch (Exception e3) {
          throw new UnexpectedAuthException("Failed to process certificate login", e3);
        }
      }
    } else {
      throw new RemoteApiException(code, e);
    }
  }

  private void checkAndThrowException(ApiException e, String userId) throws RemoteApiException {
    int code = e.getCode();

    if (sessionUnauthorized(code)) {
      throw new UnauthorizedUserException(
          "Certificate authentication is unauthorized for the requested user - ID: " + userId, e);
    } else if (sessionNoLongerEntitled(code)) {
      throw new ForbiddenAuthException(
          "Certificate authentication is forbidden for the requested user - ID: " + userId, e);
    } else {
      throw new UnexpectedAuthException(
          "Failed to process certificate login for the user - ID: " + userId, e);
    }
  }

  @Override
  public synchronized AuthenticationToken reAuthSessionOrThrow(String sessionToken, int code, Exception e)
      throws RemoteApiException {
    AuthenticationContext authContext = contextForSessionToken(sessionToken);
    reAuthOrThrow(authContext.getUserId(), code, e);
    return authContext.getToken();
  }

  /**
   * Evaluate if the session needs to be invalidated
   * @return
   */
  private boolean shouldInvalidateSession(String userId) {
    Long timeSinceLastAuthMillis =
        System.currentTimeMillis() - contextForUser(userId).getToken().getAuthenticationTime();
    return timeSinceLastAuthMillis > MAX_SESSION_TIME_MILLIS;
  }

  /**
   * Verify the response code. If the response code identifies user not entitled to perform the
   * action then return true, otherwise return false.
   * @param code response code
   * @return
   */
  @Override
  public boolean sessionNoLongerEntitled(int code) {
    return validateResponseCode(Status.FORBIDDEN, code);
  }

  /**
   * Verify the response code. If the response code identifies user not authorized to perform the
   * action then return true, otherwise return false.
   * @param code response code
   * @return
   */
  @Override
  public boolean sessionUnauthorized(int code) {
    return validateResponseCode(Status.UNAUTHORIZED, code);
  }

  private boolean validateResponseCode(Status expectedStatus, int code) {
    Status status = Status.fromStatusCode(code);
    return expectedStatus.equals(status);
  }

  /**
   * Should be invoked by integration to register their users and the corresponding keystores.
   */
  @Override
  public void registerUser(String userId, KeyStore keyStore, String keyStorePass) {
    authContexts.put(userId, new AuthenticationContext(userId, keyStore, keyStorePass));
  }

  /**
   * Retrieves a client build with the proper SSL context for the user.
   */
  @Override
  public Client httpClientForUser(String userId) {
    return authContexts.get(userId).httpClientForContext();
  }

  /**
   * Retrieves a client build with the proper SSL context for the user.
   */
  @Override
  public Client httpClientForSessionToken(String sessionToken) {
    return contextForSessionToken(sessionToken).httpClientForContext();
  }

}
