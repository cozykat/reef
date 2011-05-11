/**
 * Copyright 2011 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  Green Energy Corp licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.totalgrid.reef.api

/**
 * A subscription object provides header info and can also be canceled. It carries the message type
 * primarily to make message signatures more expressive.
 * TODO: add ISubscriptions to scala apis
 */
trait ISubscription[SubscriptionMessageType] extends IHeaderInfo {
  def cancel()

  def start()
}

object ISubscription {
  /**
   * convert a ISubscription to the RequestEnv used in scala SyncOps
   * TODO: rationalize RequestEnv and ISubscription interfaces
   */
  implicit def convertISubToRequestEnv(sub: ISubscription[_]): RequestEnv = {
    val serviceHeaders = new ServiceHandlerHeaders()
    sub.setHeaders(serviceHeaders)
    serviceHeaders.env
  }
}