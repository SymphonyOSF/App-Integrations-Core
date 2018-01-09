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

package org.symphonyoss.integration.provisioning.exception;

import org.symphonyoss.integration.exception.IntegrationRuntimeException;

/**
 * Created by rsanchez on 29/06/16.
 */
public class ConfigurationProvisioningException extends IntegrationRuntimeException {

  private static final String COMPONENT = "Configuration Provisioning";

  public ConfigurationProvisioningException(String message, Throwable cause, String... solutions) {
    super(COMPONENT, message, cause, solutions);
  }
}
