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
package org.totalgrid.reef.client.sapi.client.rest.impl

import net.agileautomata.executor4s._

import org.totalgrid.reef.broker._
import org.totalgrid.reef.client.proto.Envelope
import org.totalgrid.reef.client.proto.SimpleAuth.AuthRequest
import org.totalgrid.reef.client.sapi.client.rest._
import org.totalgrid.reef.client.sapi.client._

import com.weiglewilczek.slf4s.Logging

import org.totalgrid.reef.client.sapi.types.{ BuiltInDescriptors }
import org.totalgrid.reef.client.sapi.service.AsyncService
import org.totalgrid.reef.client.types.{ ServiceTypeInformation, TypeDescriptor }
import org.totalgrid.reef.client.settings.UserSettings
import org.totalgrid.reef.client.{ Version, SubscriptionBinding, AnyNodeDestination, Routable }

final class DefaultConnection(conn: BrokerConnection, executor: Executor, timeoutms: Long)
    extends Connection
    with BrokerConnectionListener
    with Logging
    with DefaultServiceRegistry {

  addServiceInfo(BuiltInDescriptors.authRequestServiceInfo)
  addServiceInfo(BuiltInDescriptors.batchServiceRequestServiceInfo)

  conn.declareExchange("amq.direct")
  private val correlator = new ResponseCorrelator(executor)
  private val subscription = conn.listen().start(correlator)
  conn.bindQueue(subscription.getQueue, "amq.direct", subscription.getQueue)
  conn.addListener(this)

  // only called on unexpected disconnections
  def onDisconnect(expected: Boolean): Unit = {
    logger.info("connection disconnected: " + expected)
    handleDisconnect(expected)
  }

  private def handleDisconnect(expected: Boolean) {
    conn.removeListener(this)
    correlator.close()
    this.notifyListenersOfClose(expected)
  }

  // TODO: disconnect should probably block until disconnected?
  def disconnect() = {
    val currentlyConnected = conn.isConnected()
    logger.info("disconnect called, connected: " + currentlyConnected)
    subscription.close()
    if (currentlyConnected) {
      handleDisconnect(true)
      conn.disconnect()
    }
  }

  def login(authToken: String): Client = createClient(authToken, Strand(executor))

  def login(userSettings: UserSettings) = login(userSettings.getUserName, userSettings.getUserPassword)

  def login(userName: String, password: String): Promise[Client] = {
    val strand = Strand(executor)
    DefaultAnnotatedOperations.safeOperation("Error logging in with name: " + userName, strand) {
      val agent = AuthRequest.newBuilder.setName(userName).setPassword(password).setClientVersion(Version.getClientVersion).build
      def convert(response: Response[AuthRequest]): Result[Client] = response.one.map(r => createClient(r.getToken, strand))
      request(Envelope.Verb.POST, agent, BasicRequestHeaders.empty, strand).map(convert)
    }
  }

  def logout(authToken: String): Promise[Boolean] = logout(authToken, Strand(executor))
  def logout(client: Client): Promise[Boolean] = logout(client.getHeaders.getAuthToken, client)

  private def logout(authToken: String, strand: Executor): Promise[Boolean] = {
    DefaultAnnotatedOperations.safeOperation("Error revoking auth token.", strand) {
      val agent = AuthRequest.newBuilder.setToken(authToken).build
      request(Envelope.Verb.DELETE, agent, BasicRequestHeaders.empty, strand).map(r => r.one.map { _ => true })
    }
  }

  private def createClient(authToken: String, strand: Strand) = {
    val client = new DefaultClient(this, strand)
    client.modifyHeaders(_.addAuthToken(authToken))
    client
  }

  def request[A](verb: Envelope.Verb, payload: A, headers: BasicRequestHeaders, requestExecutor: Executor): Future[Response[A]] = {

    val future = requestExecutor.future[Response[A]]

    def onResponse(descriptor: TypeDescriptor[A])(result: Either[FailureResponse, Envelope.ServiceResponse]) = result match {
      case Left(response) => future.set(response)
      case Right(envelope) => future.set(RestHelpers.readServiceResponse(descriptor, envelope))

    }

    def send(info: ServiceTypeInformation[A, _]) = {
      val timeout = headers.getTimeout.getOrElse(timeoutms)
      val descriptor = info.getDescriptor

      // timeout callback will come in on a random executor thread and be marshalled correctly by future
      val uuid = correlator.register(timeout.milliseconds, onResponse(descriptor))
      try {
        val request = RestHelpers.buildServiceRequest(verb, payload, descriptor, uuid, headers)
        val replyTo = Some(BrokerDestination("amq.direct", subscription.getQueue))
        val destination = headers.getDestination.getOrElse(new AnyNodeDestination)
        conn.publish(descriptor.id, destination.getKey, request.toByteArray, replyTo)
      } catch {
        case ex: Exception =>
          correlator.fail(uuid, FailureResponse(Envelope.Status.BUS_UNAVAILABLE, ex.getMessage))
      }
    }

    ClassLookup(payload).flatMap(getServiceOption) match {
      case Some(info) => send(info)
      case None => future.set(FailureResponse(Envelope.Status.BAD_REQUEST, "No info on type: " + payload))
    }

    future
  }

  def subscribe[A](exe: Executor, descriptor: TypeDescriptor[A]) = {
    new DefaultSubscription[A](conn.listen(), exe, descriptor.deserialize)
  }

  override def bindService[A](service: AsyncService[A], exe: Executor, destination: Routable, competing: Boolean): SubscriptionBinding = {

    val serviceInfo = getServiceInfo(service.descriptor.getKlass)
    val descriptor = serviceInfo.getDescriptor

    def subscribe[A](competing: Boolean) = {
      conn.declareExchange(serviceInfo.getEventExchange)
      conn.declareExchange(descriptor.id)
      val sub = if (competing) conn.listen(descriptor.id + "_server")
      else conn.listen()
      conn.bindQueue(sub.getQueue, descriptor.id, destination.getKey)
      new DefaultServiceBinding[A](conn, sub, exe)
    }

    val sub: DefaultServiceBinding[A] = subscribe(competing)
    sub.start(service)

    sub
  }

  override def publishEvent[A](typ: Envelope.SubscriptionEventType, value: A, key: String): Unit = {
    val info = getServiceInfo(ClassLookup.get(value))
    val desc = info.getSubscriptionDescriptor.asInstanceOf[TypeDescriptor[A]]
    val event = RestHelpers.getEvent(typ, value, desc)
    conn.publish(info.getEventExchange, key, event.toByteArray)
  }

  override def bindQueueByClass[A](subQueue: String, key: String, klass: Class[A]): Unit = {
    val info = getServiceInfo(klass)
    conn.bindQueue(subQueue, info.getEventExchange, key)
  }

  override def declareEventExchange(klass: Class[_]) = {
    val info = getServiceInfo(klass)
    conn.declareExchange(info.getEventExchange)
  }

}