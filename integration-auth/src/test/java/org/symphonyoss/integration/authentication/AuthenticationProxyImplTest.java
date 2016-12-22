package org.symphonyoss.integration.authentication;

import static com.symphony.atlas.config.SymphonyAtlas.KEY_AUTH_URL;
import static com.symphony.atlas.config.SymphonyAtlas.SESSION_AUTH_URL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.symphony.api.auth.client.ApiException;
import com.symphony.api.auth.model.Token;
import com.symphony.atlas.AtlasException;
import com.symphony.atlas.IAtlas;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.symphonyoss.integration.IntegrationAtlas;
import org.symphonyoss.integration.authentication.exception.AuthUrlNotFoundException;
import org.symphonyoss.integration.authentication.exception.KeyManagerConnectivityException;
import org.symphonyoss.integration.authentication.exception.PodConnectivityException;
import org.symphonyoss.integration.exception.authentication.UnexpectedAuthException;
import org.symphonyoss.integration.exception.RemoteApiException;

import java.io.IOException;
import java.security.KeyStore;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.Response;

/**
 * Created by rsanchez on 10/05/16.
 */
@RunWith(MockitoJUnitRunner.class)
public class AuthenticationProxyImplTest {

  private static final String JIRAWEBHOOK = "jirawebhook";

  private static final String SIMPLEWEBHOOK = "simplewebhook";

  private static final String SESSION_TOKEN =
      "b8b37d5aa121fd5947b05b53a3b2e3d2fe0e88105b7e99b76301ed2fa0e52f3fa190c6b099c2a5dce8f9ef2f4c"
          + "f76d80023bdf77d0a89f20da8caff181ec3ba2";

  private static final String KM_TOKEN =
      "010097b1fe69e9cf7adb7535d809d6cd9aaff59d381f9c80b768dccd622a9cb8292ff6a81396"
          +
          "ddba8971e9861863d497fcd7d8971837c2c10a91fc2a2e6a3a30d8830655133d188ecec7feba437a31f093f"
          +
          "aa8849283e5e04700ff6dc7fa7def1b9f96fc6c2e08946aecbb6b59db1d565eafb623757fd788477e8d8edf"
          +
          "bfbf6bc202d863b15bfaeb9a9b38d861b97111ada7a2c66f5f44c9c1fda1d7623cc14b1f310514fe5fab07a"
          + "65d94e366609a141fd7";

  private static final String SESSION_TOKEN2 = "ae96960b-76ec-44f0-8fc0-3b8f33b698ef";

  private static final String KM_TOKEN2 = "3975bd7f-a6c1-4ec4-806d-c241991889a1";

  private IAtlas atlas;

  @Mock
  private AuthenticationApiDecorator sbeAuthApi;

  @Mock
  private AuthenticationApiDecorator keyManagerAuthApi;

  @Mock
  private IntegrationAtlas integrationAtlas;

  @Mock
  private KeyStore jiraKs;

  @Mock
  private KeyStore simpleKs;

  @InjectMocks
  private AuthenticationProxyImpl proxy = new AuthenticationProxyImpl();
  private Token sessionToken = new Token();
  private Token kmToken = new Token();
  private Token sessionToken2 = new Token();
  private Token kmToken2 = new Token();

  @Before
  public void setup() {
    this.proxy.registerUser(JIRAWEBHOOK, jiraKs, "");
    this.proxy.registerUser(SIMPLEWEBHOOK, simpleKs, "");

    this.atlas = mock(IAtlas.class);
    when(integrationAtlas.getAtlas()).thenReturn(atlas);

    sessionToken.setName("sessionToken");
    sessionToken.setToken(SESSION_TOKEN);

    kmToken.setName("keyManagerToken");
    kmToken.setToken(KM_TOKEN);

    sessionToken2.setName("sessionToken");
    sessionToken2.setToken(SESSION_TOKEN2);

    kmToken2.setName("keyManagerToken");
    kmToken2.setToken(KM_TOKEN2);
  }

  @Test(expected = AuthUrlNotFoundException.class)
  public void testInitWithoutSBEUrl() {
    proxy.init();
  }

  @Test(expected = AuthUrlNotFoundException.class)
  public void testInitWithoutKeyManagerUrl() {
    when(atlas.get(SESSION_AUTH_URL)).thenReturn("https://localhost:8444");
    proxy.init();
  }

  @Test
  public void testInit() throws AtlasException {
    when(atlas.get(SESSION_AUTH_URL)).thenReturn("https://localhost:8444");
    when(atlas.get(KEY_AUTH_URL)).thenReturn("https://localhost:8444/relay");
    proxy.init();
  }

  @Test
  public void testFailAuthenticationSBE() throws ApiException {
    when(sbeAuthApi.v1AuthenticatePost(JIRAWEBHOOK)).thenThrow(ApiException.class);
    when(atlas.get(SESSION_AUTH_URL)).thenReturn("https://localhost:8444");
    when(atlas.get(KEY_AUTH_URL)).thenReturn("https://localhost:8444/relay");

    validateFailedAuthentication();
  }

  @Test
  public void testFailAuthenticationKeyManager() throws ApiException {
    Token sessionToken = new Token();
    sessionToken.setName("sessionToken");

    when(sbeAuthApi.v1AuthenticatePost(JIRAWEBHOOK)).thenReturn(sessionToken);
    when(keyManagerAuthApi.v1AuthenticatePost(JIRAWEBHOOK)).thenThrow(ApiException.class);
    when(atlas.get(SESSION_AUTH_URL)).thenReturn("https://localhost:8444");
    when(atlas.get(KEY_AUTH_URL)).thenReturn("https://localhost:8444/relay");

    validateFailedAuthentication();
  }

  private void validateFailedAuthentication() {
    try {
      proxy.authenticate(JIRAWEBHOOK);
      fail();
    } catch (ApiException e) {
      assertTrue(proxy.getToken(JIRAWEBHOOK).equals(AuthenticationToken.VOID_AUTH_TOKEN));
      assertFalse(proxy.isAuthenticated(JIRAWEBHOOK));
    }
  }

  @Test(expected = PodConnectivityException.class)
  public void testFailAuthenticationPodConnectivityException() throws ApiException {
    ProcessingException exception = new ProcessingException(new IOException());

    when(sbeAuthApi.v1AuthenticatePost(JIRAWEBHOOK)).thenThrow(exception);
    when(atlas.get(SESSION_AUTH_URL)).thenReturn("https://localhost:8444");
    when(atlas.get(KEY_AUTH_URL)).thenReturn("https://localhost:8444/relay");

    proxy.authenticate(JIRAWEBHOOK);
  }

  @Test(expected = ProcessingException.class)
  public void testFailAuthenticationPodProcessingException() throws ApiException {
    when(sbeAuthApi.v1AuthenticatePost(JIRAWEBHOOK)).thenThrow(ProcessingException.class);
    when(atlas.get(SESSION_AUTH_URL)).thenReturn("https://localhost:8444");
    when(atlas.get(KEY_AUTH_URL)).thenReturn("https://localhost:8444/relay");

    proxy.authenticate(JIRAWEBHOOK);
  }

  @Test(expected = KeyManagerConnectivityException.class)
  public void testFailAuthenticationKMConnectivityException() throws ApiException {
    Token sessionToken = new Token();
    sessionToken.setName("sessionToken");

    ProcessingException exception = new ProcessingException(new IOException());

    when(sbeAuthApi.v1AuthenticatePost(JIRAWEBHOOK)).thenReturn(sessionToken);
    when(keyManagerAuthApi.v1AuthenticatePost(JIRAWEBHOOK)).thenThrow(exception);
    when(atlas.get(SESSION_AUTH_URL)).thenReturn("https://localhost:8444");
    when(atlas.get(KEY_AUTH_URL)).thenReturn("https://localhost:8444/relay");

    proxy.authenticate(JIRAWEBHOOK);
  }

  @Test(expected = ProcessingException.class)
  public void testFailAuthenticationKMProcessingException() throws ApiException {
    Token sessionToken = new Token();
    sessionToken.setName("sessionToken");

    when(sbeAuthApi.v1AuthenticatePost(JIRAWEBHOOK)).thenReturn(sessionToken);
    when(keyManagerAuthApi.v1AuthenticatePost(JIRAWEBHOOK)).thenThrow(ProcessingException.class);
    when(atlas.get(SESSION_AUTH_URL)).thenReturn("https://localhost:8444");
    when(atlas.get(KEY_AUTH_URL)).thenReturn("https://localhost:8444/relay");

    proxy.authenticate(JIRAWEBHOOK);
  }

  @Test
  public void testAuthentication() throws ApiException {
    when(atlas.get("session.auth.url")).thenReturn("https://localhost:8444");
    when(atlas.get("keymanager.auth.url")).thenReturn("https://localhost:8444/relay");

    when(sbeAuthApi.v1AuthenticatePost(JIRAWEBHOOK)).thenReturn(sessionToken);
    when(keyManagerAuthApi.v1AuthenticatePost(JIRAWEBHOOK)).thenReturn(kmToken);

    when(sbeAuthApi.v1AuthenticatePost(SIMPLEWEBHOOK)).thenReturn(sessionToken2);
    when(keyManagerAuthApi.v1AuthenticatePost(SIMPLEWEBHOOK)).thenReturn(kmToken2);

    proxy.authenticate(JIRAWEBHOOK);
    proxy.authenticate(SIMPLEWEBHOOK);

    assertTrue(proxy.isAuthenticated(JIRAWEBHOOK));
    assertEquals(sessionToken.getToken(), proxy.getToken(JIRAWEBHOOK).getSessionToken());
    assertEquals(kmToken.getToken(), proxy.getToken(JIRAWEBHOOK).getKeyManagerToken());

    assertTrue(proxy.isAuthenticated(SIMPLEWEBHOOK));
    assertEquals(sessionToken2.getToken(), proxy.getToken(SIMPLEWEBHOOK).getSessionToken());
    assertEquals(kmToken2.getToken(), proxy.getToken(SIMPLEWEBHOOK).getKeyManagerToken());
  }

  @Test
  public void testInvalidate() throws ApiException {
    testAuthentication();

    proxy.invalidate(JIRAWEBHOOK);
    assertFalse(proxy.isAuthenticated(JIRAWEBHOOK));
    assertTrue(proxy.getToken(JIRAWEBHOOK).getSessionToken().equals(sessionToken.getToken()));
    assertTrue(proxy.getToken(JIRAWEBHOOK).getKeyManagerToken().equals(kmToken.getToken()));
  }

  @Test
  public void testSessionNoLongerEntitled() {
    assertTrue(proxy.sessionNoLongerEntitled(Response.Status.FORBIDDEN.getStatusCode()));
  }

  @Test
  public void testSessionEntitled() {
    assertFalse(proxy.sessionNoLongerEntitled(Response.Status.UNAUTHORIZED.getStatusCode()));
  }

  @Test(expected = RemoteApiException.class)
  public void testReAuthThrowRemoteApiException() throws RemoteApiException {
    proxy.reAuthOrThrow(JIRAWEBHOOK, 500, new RuntimeException());
  }

  @Test(expected = UnexpectedAuthException.class)
  public void testReAuthFailed() throws RemoteApiException, ApiException {
    when(sbeAuthApi.v1AuthenticatePost(JIRAWEBHOOK)).thenThrow(new ApiException());
    proxy.reAuthOrThrow(JIRAWEBHOOK, 401, new RuntimeException());
  }

  @Test
  public void testReAuth() throws ApiException, RemoteApiException {
    when(sbeAuthApi.v1AuthenticatePost(JIRAWEBHOOK)).thenReturn(new Token());
    when(keyManagerAuthApi.v1AuthenticatePost(JIRAWEBHOOK)).thenReturn(new Token());
    proxy.reAuthOrThrow(JIRAWEBHOOK, 401, new RuntimeException());
  }
}
