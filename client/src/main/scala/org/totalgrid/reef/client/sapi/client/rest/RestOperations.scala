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
package org.totalgrid.reef.client.sapi.client.rest

import org.totalgrid.reef.client.sapi.client._
import org.totalgrid.reef.client.proto.Envelope.Verb

import net.agileautomata.executor4s.Future
import org.totalgrid.reef.client.types.TypeDescriptor
import org.totalgrid.reef.client.sapi.service.AsyncService
import org.totalgrid.reef.client.{ Subscription, SubscriptionBinding }

/**
 * api-implementer facing interface that encapsulates the get/put/post/delete operations without revealing which
 * executor our request is going to be returned to
 */
trait RestOperations {

  def request[A](verb: Verb, payload: A, headers: Option[BasicRequestHeaders]): Future[Response[A]]

  final def get[A](payload: A, headers: BasicRequestHeaders) = request(Verb.GET, payload, Some(headers))
  final def delete[A](payload: A, headers: BasicRequestHeaders) = request(Verb.DELETE, payload, Some(headers))
  final def post[A](payload: A, headers: BasicRequestHeaders) = request(Verb.POST, payload, Some(headers))
  final def put[A](payload: A, headers: BasicRequestHeaders) = request(Verb.PUT, payload, Some(headers))

  final def get[A](payload: A) = request(Verb.GET, payload, None)
  final def delete[A](payload: A) = request(Verb.DELETE, payload, None)
  final def post[A](payload: A) = request(Verb.POST, payload, None)
  final def put[A](payload: A) = request(Verb.PUT, payload, None)

}

/**
 * api-implementer facing interface that allows us to ask for a subscription or serviceBinding without worrying
 * about which executor it is using
 */
trait ClientBindOperations {
  /**
   * subscribe returns a Future to the result that is always going to be set when it is returned, it is
   * returned as a future so a client who wants to listen to the SubscriptionResult will get the event
   * on the same dispatcher as the result would come on
   */
  def subscribe[A](descriptor: TypeDescriptor[A]): Subscription[A]

  /**
   * setups a service listener to the published "request exchange" associated with the service type A; binding must be
   * done later by an authorized agent with "services" level access to the broker using the bindServiceQueue() function.
   */
  def lateBindService[A](service: AsyncService[A]): SubscriptionBinding
}