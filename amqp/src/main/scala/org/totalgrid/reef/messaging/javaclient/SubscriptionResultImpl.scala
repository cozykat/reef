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

package org.totalgrid.reef.messaging.javaclient

import org.totalgrid.reef.api.Subscription
import org.totalgrid.reef.api.javaclient.{ IEventAcceptor, ISubscriptionResult, ISubscription }

/**
 * Wrapper around the scala based subscription
 */
class SubscriptionWrapper[A](subscription: Subscription[A]) extends ISubscription[A] {
  def start(callback: IEventAcceptor[A]) = subscription.start(callback.onEvent _)

  def getId() = subscription.id

  def cancel() = subscription.cancel

  override def toString: String =
    {
      "SubscriptionWrapper{ " + subscription + " }"
    }
}

class SubscriptionResult[ResultType, SubscriptionType](result: ResultType, subscription: Subscription[SubscriptionType])
    extends ISubscriptionResult[ResultType, SubscriptionType] {
  override def getResult: ResultType = result

  override def getSubscription: ISubscription[SubscriptionType] = new SubscriptionWrapper(subscription)

  override def toString: String =
    {
      "SubscriptionResult{ " + getSubscription + ", results: " + getResult + " }"
    }
}