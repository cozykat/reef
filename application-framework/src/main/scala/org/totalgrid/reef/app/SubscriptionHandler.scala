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
package org.totalgrid.reef.app

import org.totalgrid.reef.japi.client.SubscriptionResult
import org.totalgrid.reef.util.Cancelable
import org.totalgrid.reef.executor.Executor

trait SubscriptionHandler[A] {

  def setSubscription(result: SubscriptionResult[List[A], A], exe: Executor): Cancelable

  def cancel()
}

trait SubscriptionHandlerBase[A] extends SubscriptionHandler[A] with Cancelable { self: ServiceContext[A] =>

  var subscription: Option[Cancelable] = None

  def setSubscription(result: SubscriptionResult[List[A], A], exe: Executor) = {
    if (subscription.isDefined) throw new IllegalArgumentException("Subscription already set.")
    subscription = Some(ServiceContext.attachToServiceContext(result, this, exe))
    this
  }

  def cancel() = {
    subscription.foreach(_.cancel())
    this.clear
    subscription = None
  }
}
