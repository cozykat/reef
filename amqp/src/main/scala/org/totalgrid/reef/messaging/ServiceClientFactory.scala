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
package org.totalgrid.reef.messaging

import org.totalgrid.reef.api.ServiceList
import org.totalgrid.reef.api.scalaclient.Subscription

/**
 * factory trait that defines what we need to construct ClientSessions and subscriptions
 */
trait ClientSessionFactory {

  /**
   * the factory must create and start a ServiceResponseCorrelator that will be shared by all clients
   */
  def getServiceResponseCorrelator(timeoutms: Long): ServiceResponseCorrelator

  def getProtoClientSession(lookup: ServiceList, timeoutms: Long) = new ProtoClient(this, lookup, timeoutms)

  /**
   * the factory must create subscription objects of the appropriate type even if its a "stream type"
   */
  def prepareSubscription[A](deserialize: Array[Byte] => A, subIsStreamType: Boolean): Subscription[A]

}