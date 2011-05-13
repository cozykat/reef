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

import com.google.protobuf.GeneratedMessage

import org.totalgrid.reef.api.{ ServiceHandlerHeaders, RequestEnv, ITypeDescriptor, ISubscription, ServiceTypes }
import org.totalgrid.reef.api.javaclient.{ ISession, IEventAcceptor, IPromise, IResult, IResultAcceptor }

import ServiceTypes._

import org.totalgrid.reef.messaging.ProtoClient

import _root_.scala.collection.JavaConversions._
import org.totalgrid.reef.api._
import scalaclient.ClientSession

/**
 * wraps a ProtoClient with some java helpers to convert to and from java lists
 */
class Session(client: ClientSession) extends ISession {

  def request[A <: AnyRef](verb: Envelope.Verb, payload: A, env: ServiceHandlerHeaders): java.util.List[A] = client.requestThrow(verb, payload, env.env)

  def get[A <: AnyRef](payload: A): java.util.List[A] = client.getOrThrow(payload)
  def delete[A <: AnyRef](payload: A): java.util.List[A] = client.deleteOrThrow(payload)
  def post[A <: AnyRef](payload: A): java.util.List[A] = client.postOrThrow(payload)
  def put[A <: AnyRef](payload: A): java.util.List[A] = client.putOrThrow(payload)

  private implicit def convertEnv(hdr: IHeaderInfo): RequestEnv = {
    val headers = new ServiceHandlerHeaders(new RequestEnv)
    hdr.setHeaders(headers)
    headers.env
  }

  def get[A <: AnyRef](payload: A, hdr: IHeaderInfo): java.util.List[A] = client.getOrThrow(payload, hdr)
  def delete[A <: AnyRef](payload: A, hdr: IHeaderInfo): java.util.List[A] = client.deleteOrThrow(payload, hdr)
  def put[A <: AnyRef](payload: A, hdr: IHeaderInfo): java.util.List[A] = client.putOrThrow(payload, hdr)
  def post[A <: AnyRef](payload: A, hdr: IHeaderInfo): java.util.List[A] = client.postOrThrow(payload, hdr)

  def getOne[A <: AnyRef](payload: A): A = client.getOneOrThrow(payload)
  def deleteOne[A <: AnyRef](payload: A): A = client.deleteOneOrThrow(payload)
  def putOne[A <: AnyRef](payload: A): A = client.putOneOrThrow(payload)
  def postOne[A <: AnyRef](payload: A): A = client.postOneOrThrow(payload)

  def getOne[A <: AnyRef](payload: A, hdr: IHeaderInfo): A = client.getOneOrThrow(payload, hdr)
  def deleteOne[A <: AnyRef](payload: A, hdr: IHeaderInfo): A = client.deleteOneOrThrow(payload, hdr)
  def putOne[A <: AnyRef](payload: A, hdr: IHeaderInfo): A = client.putOneOrThrow(payload, hdr)
  def postOne[A <: AnyRef](payload: A, hdr: IHeaderInfo): A = client.postOneOrThrow(payload, hdr)

  implicit def convert[A](fun: () => MultiResult[A]): IPromise[IResult[A]] = new Promise(fun)

  def getFuture[A <: AnyRef](payload: A): IPromise[IResult[A]] = client.getWithFuture(payload)
  def deleteFuture[A <: AnyRef](payload: A): IPromise[IResult[A]] = client.deleteWithFuture(payload)
  def postFuture[A <: AnyRef](payload: A): IPromise[IResult[A]] = client.postWithFuture(payload)
  def putFuture[A <: AnyRef](payload: A): IPromise[IResult[A]] = client.putWithFuture(payload)

  def getFuture[A <: AnyRef](payload: A, hdr: IHeaderInfo): IPromise[IResult[A]] = client.getWithFuture(payload, hdr)
  def deleteFuture[A <: AnyRef](payload: A, hdr: IHeaderInfo): IPromise[IResult[A]] = client.deleteWithFuture(payload, hdr)
  def postFuture[A <: AnyRef](payload: A, hdr: IHeaderInfo): IPromise[IResult[A]] = client.postWithFuture(payload, hdr)
  def putFuture[A <: AnyRef](payload: A, hdr: IHeaderInfo): IPromise[IResult[A]] = client.putWithFuture(payload, hdr)

  /* -------- Asynchronous API ------ */

  implicit def convert[A](callback: IResultAcceptor[A]): MultiResult[A] => Unit =
    (result: MultiResult[A]) => callback.onResult(new Result(result))

  def getAsync[A <: AnyRef](payload: A, callback: IResultAcceptor[A]) = client.asyncGet(payload)(callback)
  def deleteAsync[A <: AnyRef](payload: A, callback: IResultAcceptor[A]) = client.asyncDelete(payload)(callback)
  def postAsync[A <: AnyRef](payload: A, callback: IResultAcceptor[A]) = client.asyncPost(payload)(callback)
  def putAsync[A <: AnyRef](payload: A, callback: IResultAcceptor[A]) = client.asyncPut(payload)(callback)

  def addSubscription[A <: GeneratedMessage](pd: ITypeDescriptor[A], ea: IEventAcceptor[A]): ISubscription[A] = {
    client.addSubscription(pd.getKlass, ea.onEvent)
  }

  // we create a defaultEnv here and pass it to the underlying ClientSession so we keep a reference to a request
  // env that we control and can update, the underlying client will see any updates
  // TODO: make defaultEnv immutable
  private val defaultEnv = new ServiceHandlerHeaders(new RequestEnv)
  client.setDefaultHeaders(defaultEnv.env)

  override def getDefaultEnv = defaultEnv

  def close() = client.close()

  def getUnderlyingClient() = client
}

