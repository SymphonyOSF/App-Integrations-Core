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

import com.symphony.api.auth.client.ApiClient;
import com.symphony.api.auth.client.ApiException;
import com.symphony.api.auth.client.Pair;
import com.symphony.api.auth.client.TypeRef;
import com.symphony.api.auth.client.auth.Authentication;

import com.codahale.metrics.Timer;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.MultiPart;
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart;
import org.symphonyoss.integration.authentication.metrics.ApiMetricsController;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * This class has been created to instantiate web-service clients with specific SSL context.
 * It overrides Swagger auto-generated code that instantiates a standard web-service client with no SSL context.
 * Swagger code is not meant to be extended, and therefore we had to forcefully set some of the private fields of Swagger's ApiClient.
 * Created by ecarrenho on 8/23/16.
 */
public class AuthApiClientDecorator extends ApiClient {

  private Map<String, String> defaultHeaderMap;
  private Map<String, Authentication> authentications;

  private int statusCode;
  private Map<String, List<String>> responseHeaders;

  private AuthenticationProxy authenticationProxy;

  private ApiMetricsController metricsController;

  public AuthApiClientDecorator(AuthenticationProxy authenticationProxy,
      ApiMetricsController metricsController) {
    this.authenticationProxy = authenticationProxy;
    this.metricsController = metricsController;

    try {
      Field defaultHeaderMapField = ApiClient.class.getDeclaredField("defaultHeaderMap");
      defaultHeaderMapField.setAccessible(true);
      this.defaultHeaderMap = (Map<String, String>) defaultHeaderMapField.get((ApiClient) this);

      Field authenticationsField = ApiClient.class.getDeclaredField("authentications");
      authenticationsField.setAccessible(true);
      this.authentications = (Map<String, Authentication>) authenticationsField.get((ApiClient) this);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException("Missing parent attributes. Swagger auto-generated code changed and this class requires refactoring.", e);
    }
  }

  /**
   * Gets the status code of the previous request
   */
  public int getStatusCode() {
    return statusCode;
  }

  /**
   * Gets the response headers of the previous request
   */
  public Map<String, List<String>> getResponseHeaders() {
    return responseHeaders;
  }

  /**
   * Update query and header parameters based on authentication settings.
   *
   * @param authNames The authentications to apply
   */
  private void updateParamsForAuth(String[] authNames, List<Pair> queryParams, Map<String, String> headerParams) {
    for (String authName : authNames) {
      Authentication auth = authentications.get(authName);
      if (auth == null) {
        throw new RuntimeException("Authentication undefined: " + authName);
      }
      auth.applyToParams(queryParams, headerParams);
    }
  }

  private Map<String, List<String>> buildResponseHeaders(Response response) {
    Map<String, List<String>> responseHeaders = new HashMap<String, List<String>>();
    for (Map.Entry<String, List<Object>> entry : response.getHeaders().entrySet()) {
      List<Object> values = entry.getValue();
      List<String> headers = new ArrayList<String>();
      for (Object o : values) {
        headers.add(String.valueOf(o));
      }
      responseHeaders.put(entry.getKey(), headers);
    }
    return responseHeaders;
  }


  /**
   * Invoke API by sending HTTP request with the given options.
   *
   * @param path The sub-path of the HTTP URL
   * @param method The request method, one of "GET", "POST", "PUT", and "DELETE"
   * @param queryParams The query parameters
   * @param body The request body object
   * @param headerParams The header parameters
   * @param formParams The form parameters
   * @param accept The request's Accept header
   * @param contentType The request's Content-Type header
   * @param authNames The authentications to apply
   * @param returnType The return type into which to deserialize the response
   * @return The response body in type of string
   */
  public <T> T invokeAPI(String userId, String path, String method, List<Pair> queryParams,
      Object body, Map<String, String> headerParams, Map<String, Object> formParams, String accept,
      String contentType, String[] authNames, TypeRef returnType) throws ApiException {
    Timer.Context context = null;
    boolean success = false;

    try {
      context = metricsController.startApiCall(path);

      T result = executeApi(userId, path, method, queryParams, body, headerParams, formParams,
          accept, contentType, authNames, returnType);
      success = true;

      return result;
    } finally {
      metricsController.finishApiCall(context, path, success);
    }

  }

  private <T> T executeApi(String userId, String path, String method, List<Pair> queryParams,
      Object body, Map<String, String> headerParams, Map<String, Object> formParams, String accept,
      String contentType, String[] authNames, TypeRef returnType) throws ApiException {
    ApiClientDecoratorUtils.setHeaderTraceId(headerParams);
    updateParamsForAuth(authNames, queryParams, headerParams);

    // The code block that builds the web-service client has been modified from the original swagger auto-generated code.
    // Swagger used a standard ClientBuilder and used the system (global) key store. Here, a custom ClientBuilder is
    // requested to the Authentication Proxy, configured with a key store specific to the integration user.
    final Client client = authenticationProxy.httpClientForUser(userId);

    WebTarget target = client.target(getBasePath()).path(path);

    if (queryParams != null) {
      for (Pair queryParam : queryParams) {
        if (queryParam.getValue() != null) {
          target = target.queryParam(queryParam.getName(), queryParam.getValue());
        }
      }
    }

    Invocation.Builder invocationBuilder = target.request().accept(accept);

    for (String key : headerParams.keySet()) {
      String value = headerParams.get(key);
      if (value != null) {
        invocationBuilder = invocationBuilder.header(key, value);
      }
    }

    for (String key : defaultHeaderMap.keySet()) {
      if (!headerParams.containsKey(key)) {
        String value = defaultHeaderMap.get(key);
        if (value != null) {
          invocationBuilder = invocationBuilder.header(key, value);
        }
      }
    }

    Entity<?> formEntity = null;

    if (contentType.startsWith("multipart/form-data")) {
      MultiPart multipart = new MultiPart();
      for (Map.Entry<String, Object> param : formParams.entrySet()) {
        if (param.getValue() instanceof File) {
          File file = (File) param.getValue();

          FormDataMultiPart mp = new FormDataMultiPart();
          mp.bodyPart(new FormDataBodyPart(param.getKey(), file.getName()));
          multipart.bodyPart(mp, MediaType.MULTIPART_FORM_DATA_TYPE);

          multipart.bodyPart(new FileDataBodyPart(param.getKey(), file, MediaType.APPLICATION_OCTET_STREAM_TYPE));
        } else {
          FormDataMultiPart mp = new FormDataMultiPart();
          mp.bodyPart(new FormDataBodyPart(param.getKey(), parameterToString(param.getValue())));
          multipart.bodyPart(mp, MediaType.MULTIPART_FORM_DATA_TYPE);
        }
      }
      formEntity = Entity.entity(multipart, MediaType.MULTIPART_FORM_DATA_TYPE);
    } else if (contentType.startsWith("application/x-www-form-urlencoded")) {
      Form form = new Form();
      for (Map.Entry<String, Object> param : formParams.entrySet()) {
        form.param(param.getKey(), parameterToString(param.getValue()));
      }
      formEntity = Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE);
    }

    Response response = null;

    if ("GET".equals(method)) {
      response = invocationBuilder.get();
    } else if ("POST".equals(method)) {
      if (formEntity != null) {
        response = invocationBuilder.post(formEntity);
      } else if (body == null) {
        response = invocationBuilder.post(null);
      } else {
        response = invocationBuilder.post(serialize(body, contentType));
      }
    } else if ("PUT".equals(method)) {
      if (formEntity != null) {
        response = invocationBuilder.put(formEntity);
      } else if (body == null) {
        response = invocationBuilder.put(null);
      } else {
        response = invocationBuilder.put(serialize(body, contentType));
      }
    } else if ("DELETE".equals(method)) {
      response = invocationBuilder.delete();
    } else {
      throw new ApiException(500, "unknown method type " + method);
    }

    statusCode = response.getStatusInfo().getStatusCode();
    responseHeaders = buildResponseHeaders(response);

    if (response.getStatus() == Response.Status.NO_CONTENT.getStatusCode()) {
      return null;
    } else if (response.getStatusInfo().getFamily().equals(Response.Status.Family.SUCCESSFUL)) {
      if (returnType == null) {
        return null;
      } else {
        return deserialize(response, returnType);
      }
    } else {
      String message = "error";
      String respBody = null;
      if (response.hasEntity()) {
        try {
          respBody = String.valueOf(response.readEntity(String.class));
          message = respBody;
        } catch (RuntimeException e) {
          // e.printStackTrace();
        }
      }
      throw new ApiException(
          response.getStatus(),
          message,
          buildResponseHeaders(response),
          respBody);
    }
  }

}
