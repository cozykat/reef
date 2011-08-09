/**
 * Copyright 2011 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the GNU Affero General Public License
 * Version 3.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.gnu.org/licenses/agpl.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.totalgrid.reef.services.framework

import org.totalgrid.reef.sapi.RequestEnv
import org.totalgrid.reef.messaging.serviceprovider.ServiceSubscriptionHandler
import org.totalgrid.reef.event.SystemEventSink

/**
 * the request context is handed through the service call chain. It allows us to make the services and models
 * stateless objects by concentrating all of the per-request state in one object. It also provides the sinks for
 * common operations that all services use (subscription publishing, operation enqueuing and event generation).
 *
 * TODO: refactor auth service to use requestContext
 */
trait RequestContext {

  /**
   * the operation buffer is used to delay the creation and publishing of service events (ADDED,MODIFIED,DELETED) until
   * the appropriate time.
   */
  def operationBuffer: OperationBuffer

  /**
   * subscription handler that handles the publish and bind calls. Differs from the original subHandler since it will
   * accept any service event and lookup the exchange rather than needing a different publisher for each object type
   */
  def subHandler: ServiceSubscriptionHandler

  /**
   * for publishing system messages (System.LogOn, Subsystem.Starting) etc, publishes these messages immediately and
   * even if the rest of the transaction rolls back
   */
  def eventSink: SystemEventSink

  /**
   * request headers as received from the client
   */
  def headers: RequestEnv
}

/**
 * a RequestContextSource provides a transaction function which will generate a new RequestContext and once the client
 * function has completed make sure to cleanup after the transaction (publish subscription messages etc). transaction
 * should be called as high up in the call chain as possible
 */
trait RequestContextSource {
  def transaction[A](f: RequestContext => A): A
}

/**
 * wrapper class that takes a source and merges in some extra RequestEnv headers before the transaction
 */
class RequestContextSourceWithHeaders(contextSource: RequestContextSource, headers: RequestEnv)
    extends RequestContextSource {
  def transaction[A](f: (RequestContext) => A) = {
    contextSource.transaction { context =>
      context.headers.merge(headers)
      f(context)
    }
  }
}

