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

package org.symphonyoss.integration.web.resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.symphonyoss.integration.authentication.api.jwt.JwtAuthentication;
import org.symphonyoss.integration.authentication.api.model.AppToken;
import org.symphonyoss.integration.authentication.api.model.JwtPayload;
import org.symphonyoss.integration.exception.RemoteApiException;
import org.symphonyoss.integration.logging.LogMessageSource;
import org.symphonyoss.integration.model.ErrorResponse;
import org.symphonyoss.integration.model.yaml.IntegrationProperties;

/**
 * REST endpoint to handle requests for manage application authentication data.
 *
 * Created by campidelli on 09/08/17.
 */
@RestController
@RequestMapping("/v1/application/{configurationId}/jwt")
public class ApplicationAuthenticationResource {

  private static final String MALFORMED_URL = "integration.web.jwt.pod.url.malformed";
  private static final String UNAUTHORIZED_URL = "integration.web.jwt.pod.url.unauthorized";
  private static final String UNAUTHORIZED_PAIR = "integration.web.jwt.pod.token.pair.invalid";
  private static final String UNAUTHORIZED_JWT = "integration.web.jwt.pod.token.jwt.invalid";

  @Autowired
  private LogMessageSource logMessage;

  @Autowired
  private IntegrationProperties properties;

  @Autowired
  private JwtAuthentication jwtAuthentication;

  /**
   * Start the JWT authentication between the App and the SBE.
   * @param configurationId Application identifier.
   * @param podUrl The POD url.
   * @return The generated Token (Ta).
   */
  @RequestMapping(value = "/authenticate")
  public ResponseEntity authenticate(@PathVariable String configurationId,
      @RequestParam String podUrl) throws RemoteApiException {

    // The requested POD URL must match with the current one that is being used
    String currentPodUrl = properties.getPodUrl();
    if (!currentPodUrl.equals(podUrl)) {
      ErrorResponse response = new ErrorResponse(
          HttpStatus.UNAUTHORIZED.value(),
          logMessage.getMessage(UNAUTHORIZED_URL, podUrl, currentPodUrl));
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    String token = jwtAuthentication.authenticate(configurationId);
    AppToken appToken = new AppToken(configurationId, token, null);

    return ResponseEntity.ok().body(appToken);
  }

  /**
   * Validate the provided JWT.
   * @param configurationId Application identifier.
   * @param jwt Json Web Token containing the user/app authentication data.
   * @return 200 OK if it's a valid pair or a 401 otherwise.
   */
  @RequestMapping(value = "/validate")
  public ResponseEntity validate(@PathVariable String configurationId, @RequestParam String jwt)
      throws RemoteApiException {
    try {
      JwtPayload jwtPayload = jwtAuthentication.parseJwtPayload(configurationId, jwt);
      return ResponseEntity.ok().body(jwtPayload.getUserId());
    } catch (Exception e) {
      ErrorResponse response = new ErrorResponse(
          HttpStatus.UNAUTHORIZED.value(),
          logMessage.getMessage(UNAUTHORIZED_JWT, jwt, e.getMessage()));
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
  }

  /**
   * Validate the provided token pair (app token and symphony token)
   * @param configurationId Application identifier.
   * @param applicationToken App token generated by the "authenticate" service.
   * @param symphonyToken Symphony token generated on the SBE based on the app token.
   * @return 200 OK if it's a valid pair or a 401 otherwise.
   */
  @RequestMapping(value = "/tokens/validate")
  public ResponseEntity validate(@PathVariable String configurationId,
      @RequestParam String applicationToken, @RequestParam String symphonyToken)
      throws RemoteApiException {

    boolean isValid = jwtAuthentication.isValidTokenPair(configurationId, applicationToken,
        symphonyToken);

    if (isValid) {
      AppToken appToken = new AppToken(configurationId, applicationToken, symphonyToken);
      return ResponseEntity.ok().body(appToken);
    }

    ErrorResponse response = new ErrorResponse(
        HttpStatus.UNAUTHORIZED.value(),
        logMessage.getMessage(UNAUTHORIZED_PAIR, applicationToken, symphonyToken));
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
  }
}
