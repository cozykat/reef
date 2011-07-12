/**
 * Copyright 2011 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. Green Energy
 * Corp licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.totalgrid.reef.sapi.client

import org.totalgrid.reef.japi.Envelope

import org.totalgrid.reef.sapi.RequestEnv

trait Subscription[A] {
  def cancel()

  def start(callback: Event[A] => Unit): Unit

  def start(callback: (Envelope.Event, A) => Unit): Unit = {
    val proxy = { (evt: Event[A]) => callback(evt.event, evt.value) }
    start(proxy)
  }

  def id(): String
}

object Subscription {
  /**
   * convert a Subscription to the RequestEnv used in scala SyncOps
   *
   * TODO should this todo really be in the scaladoc?
   * TODO: rationalize RequestEnv and Subscription interfaces
   */
  implicit def convertSubscriptionToRequestEnv(sub: Subscription[_]): RequestEnv = {
    val env = new RequestEnv
    env.setSubscribeQueue(sub.id)
    env
  }
}