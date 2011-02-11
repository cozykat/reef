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
package org.totalgrid.reef.messaging.javabridge

import org.totalgrid.reef.proto.Envelope
import com.google.protobuf.GeneratedMessage

import org.totalgrid.reef.protoapi.{ ServiceHandlerHeaders, RequestEnv, ITypeDescriptor, ISubscription }
import org.totalgrid.reef.protoapi.java.client.{ ISession, IEventAcceptor }

import org.totalgrid.reef.messaging.ProtoClient

import scala.collection.JavaConversions._

/**
 * wraps a ProtoClient with some java helpers to convert to and from java lists
 */
class Session(client: ProtoClient) extends ISession {

  def request[A <: AnyRef](verb: Envelope.Verb, payload: A, env: ServiceHandlerHeaders): java.util.List[A] = client.requestThrow(verb, payload, env.env)

  def get[A <: AnyRef](payload: A, env: ServiceHandlerHeaders): java.util.List[A] = client.getOrThrow(payload, env.env)
  def delete[A <: AnyRef](payload: A, env: ServiceHandlerHeaders): java.util.List[A] = client.deleteOrThrow(payload, env.env)
  def post[A <: AnyRef](payload: A, env: ServiceHandlerHeaders): java.util.List[A] = client.postOrThrow(payload, env.env)
  def put[A <: AnyRef](payload: A, env: ServiceHandlerHeaders): java.util.List[A] = client.putOrThrow(payload, env.env)

  def getOne[A <: AnyRef](payload: A, env: ServiceHandlerHeaders): A = client.getOneOrThrow(payload, env.env)
  def deleteOne[A <: AnyRef](payload: A, env: ServiceHandlerHeaders): A = client.deleteOneOrThrow(payload, env.env)
  def putOne[A <: AnyRef](payload: A, env: ServiceHandlerHeaders): A = client.putOneOrThrow(payload, env.env)

  def get[A <: AnyRef](payload: A, sub: ISubscription): java.util.List[A] = client.getOrThrow(payload, getEnv(sub))
  def delete[A <: AnyRef](payload: A, sub: ISubscription): java.util.List[A] = client.deleteOrThrow(payload, getEnv(sub))
  def post[A <: AnyRef](payload: A, sub: ISubscription): java.util.List[A] = client.postOrThrow(payload, getEnv(sub))
  def put[A <: AnyRef](payload: A, sub: ISubscription): java.util.List[A] = client.putOrThrow(payload, getEnv(sub))

  def getOne[A <: AnyRef](payload: A, sub: ISubscription): A = client.getOneOrThrow(payload, getEnv(sub))
  def deleteOne[A <: AnyRef](payload: A, sub: ISubscription): A = client.deleteOneOrThrow(payload, getEnv(sub))
  def putOne[A <: AnyRef](payload: A, sub: ISubscription): A = client.putOneOrThrow(payload, getEnv(sub))

  def get[A <: AnyRef](payload: A): java.util.List[A] = client.getOrThrow(payload)
  def delete[A <: AnyRef](payload: A): java.util.List[A] = client.deleteOrThrow(payload)
  def post[A <: AnyRef](payload: A): java.util.List[A] = client.postOrThrow(payload)
  def put[A <: AnyRef](payload: A): java.util.List[A] = client.putOrThrow(payload)

  def getOne[A <: AnyRef](payload: A): A = client.getOneOrThrow(payload)
  def deleteOne[A <: AnyRef](payload: A): A = client.deleteOneOrThrow(payload)
  def putOne[A <: AnyRef](payload: A): A = client.putOneOrThrow(payload)

  def addSubscription[A <: GeneratedMessage](pd: ITypeDescriptor[A], ea: IEventAcceptor[A]): ISubscription = {
    client.addSubscription(pd.getKlass, ea.onEvent)
  }

  // we create a defaultEnv here and pass it to the underlying ServiceClient so we keep a reference to a request
  // env that we control and can update, the underlying client will see any updates
  // TODO: make defaultEnv immutable
  private val defaultEnv = new ServiceHandlerHeaders(new RequestEnv)
  client.setDefaultHeaders(defaultEnv.env)

  override def getDefaultEnv = defaultEnv

  def close() = client.close

  private def getEnv(sub: ISubscription): RequestEnv = {
    val headers = new ServiceHandlerHeaders(new RequestEnv)
    sub.configure(headers)
    headers.env
  }
}

