/*
 * Copyright (c) 2026, the Dart project authors.
 *
 * Licensed under the Eclipse Public License v1.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.dart.server;

import com.google.gson.JsonObject;

import java.util.List;

public interface DartLspWorkspaceConfigurationConsumer extends Consumer {

  /**
   * The configuration of the sections that the server asked for, one entry per requested section
   * and in the same order. An entry is {@code null} if the client does not know the section.
   */
  public void computedConfiguration(List<JsonObject> configurations);
}
