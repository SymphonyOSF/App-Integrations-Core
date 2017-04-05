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

package org.symphonyoss.integration.core.bridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.symphonyoss.integration.agent.api.client.V2MessageApiClient;
import org.symphonyoss.integration.authentication.AuthenticationProxy;
import org.symphonyoss.integration.authentication.AuthenticationToken;
import org.symphonyoss.integration.exception.RemoteApiException;
import org.symphonyoss.integration.model.config.IntegrationInstance;
import org.symphonyoss.integration.model.message.Message;
import org.symphonyoss.integration.model.stream.Stream;
import org.symphonyoss.integration.model.stream.StreamType;
import org.symphonyoss.integration.pod.api.client.StreamApiClient;

import java.util.List;

/**
 * Test class responsible to test the flows in the Stream Service.
 *
 * Created by rsanchez on 13/05/16.
 */
@RunWith(MockitoJUnitRunner.class)
public class StreamServiceTest {

  private static final String INTEGRATION_USER = "jirawebhook";

  private static final String STREAM = "stream1";

  private static final Long USER_ID = 268745369L;

  @Mock
  private AuthenticationProxy authenticationProxy;

  @Mock
  private V2MessageApiClient messagesApi;

  @Mock
  private StreamApiClient streamsApi;

  @InjectMocks
  private StreamServiceImpl streamService = new StreamServiceImpl();

  @Test
  public void testGetStreamsEmpty() {
    IntegrationInstance instance = mockInstance();
    instance.setOptionalProperties("");

    List<String> streams = streamService.getStreams(instance);
    assertNotNull(streams);
    assertTrue(streams.isEmpty());

    streams = streamService.getStreams("");
    assertNotNull(streams);
    assertTrue(streams.isEmpty());
  }

  @Test
  public void testGetStreams() {
    String optionalProperties = "{ \"streams\": [ \"stream1\", \"stream2\"] }";
    IntegrationInstance instance = mockInstance();
    instance.setOptionalProperties(optionalProperties);

    List<String> streams = streamService.getStreams(instance);
    assertNotNull(streams);
    assertEquals(2, streams.size());
    assertEquals("stream1", streams.get(0));
    assertEquals("stream2", streams.get(1));

    streams = streamService.getStreams(optionalProperties);
    assertNotNull(streams);
    assertEquals(2, streams.size());
    assertEquals("stream1", streams.get(0));
    assertEquals("stream2", streams.get(1));
  }

  @Test
  public void testGetInvalidStreamType() {
    IntegrationInstance instance = mockInstance();
    instance.setOptionalProperties("");

    StreamType streamType = streamService.getStreamType(instance);
    assertEquals(StreamType.NONE, streamType);

    instance.setOptionalProperties("{ \"streamType\": \"TEST\" }");
    streamType = streamService.getStreamType(instance);
    assertEquals(StreamType.NONE, streamType);
  }

  @Test
  public void testGetStreamType() {
    IntegrationInstance instance = mockInstance();
    instance.setOptionalProperties("{ \"streamType\": \"IM\" }");

    StreamType streamType = streamService.getStreamType(instance);
    assertEquals(StreamType.IM, streamType);

    instance.setOptionalProperties("{ \"streamType\": \"CHATROOM\" }");
    streamType = streamService.getStreamType(instance);
    assertEquals(StreamType.CHATROOM, streamType);
  }

  private IntegrationInstance mockInstance() {
    IntegrationInstance instance = new IntegrationInstance();
    instance.setConfigurationId("57756bca4b54433738037005");
    instance.setInstanceId("1234");

    return instance;
  }

  @Test(expected = RemoteApiException.class)
  public void testPostMessageApiException() throws RemoteApiException {
    when(authenticationProxy.isAuthenticated(INTEGRATION_USER)).thenReturn(true);
    when(authenticationProxy.getToken(INTEGRATION_USER)).thenReturn(
        AuthenticationToken.VOID_AUTH_TOKEN);
    doThrow(RemoteApiException.class).when(messagesApi)
        .postMessage(anyString(), anyString(), anyString(), any(Message.class));

    streamService.postMessage(INTEGRATION_USER, STREAM, new Message());
  }

  @Test
  public void testPostMessageSuccessfully() throws RemoteApiException {
    Message message = new Message();
    when(authenticationProxy.isAuthenticated(INTEGRATION_USER)).thenReturn(true);
    when(authenticationProxy.getToken(INTEGRATION_USER)).thenReturn(
        AuthenticationToken.VOID_AUTH_TOKEN);
    when(messagesApi.postMessage(anyString(), anyString(), anyString(),
        any(Message.class))).thenReturn(message);

    Message result = streamService.postMessage(INTEGRATION_USER, STREAM, new Message());
    assertEquals(message, result);
  }

  @Test(expected = RemoteApiException.class)
  public void testCreateIMApiException() throws RemoteApiException {
    when(authenticationProxy.isAuthenticated(INTEGRATION_USER)).thenReturn(true);
    when(authenticationProxy.getToken(INTEGRATION_USER)).thenReturn(
        AuthenticationToken.VOID_AUTH_TOKEN);
    doThrow(RemoteApiException.class).when(streamsApi).createIM(anyString(), any(List.class));

    streamService.createIM(INTEGRATION_USER, USER_ID);
  }

  @Test
  public void testCreateIMSuccessfully() throws RemoteApiException {
    Stream stream = new Stream();
    when(authenticationProxy.isAuthenticated(INTEGRATION_USER)).thenReturn(true);
    when(authenticationProxy.getToken(INTEGRATION_USER)).thenReturn(
        AuthenticationToken.VOID_AUTH_TOKEN);
    when(streamsApi.createIM(anyString(), any(List.class))).thenReturn(stream);

    Stream result = streamService.createIM(INTEGRATION_USER, USER_ID);
    assertEquals(stream, result);
  }
}
