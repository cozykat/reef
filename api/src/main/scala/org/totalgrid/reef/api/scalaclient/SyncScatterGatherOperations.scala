package org.totalgrid.reef.api.scalaclient

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

import ProtoConversions._
import org.totalgrid.reef.api.RequestEnv
import org.totalgrid.reef.api.Envelope.Verb

trait SyncScatterGatherOperations {

  self: FutureOperations with DefaultHeaders =>

  def requestScatterGather[A <: AnyRef](verb: Verb, payloads: List[A], env: RequestEnv = getDefaultHeaders): List[MultiResult[A]] =
    payloads.map { p => requestFuture(verb, p, env) }.map { future => future() }

  def getScatterGather[A <: AnyRef](payloads: List[A], env: RequestEnv = getDefaultHeaders): List[MultiResult[A]] = requestScatterGather(Verb.GET, payloads, env)
  def deleteScatterGather[A <: AnyRef](payloads: List[A], env: RequestEnv = getDefaultHeaders): List[MultiResult[A]] = requestScatterGather(Verb.DELETE, payloads, env)
  def putScatterGather[A <: AnyRef](payloads: List[A], env: RequestEnv = getDefaultHeaders): List[MultiResult[A]] = requestScatterGather(Verb.PUT, payloads, env)
  def postScatterGather[A <: AnyRef](payloads: List[A], env: RequestEnv = getDefaultHeaders): List[MultiResult[A]] = requestScatterGather(Verb.POST, payloads, env)

  def getOneScatterGather[A <: AnyRef](payloads: List[A], env: RequestEnv = getDefaultHeaders): List[SingleResult[A]] = getScatterGather(payloads, env)
  def deleteOneScatterGather[A <: AnyRef](payloads: List[A], env: RequestEnv = getDefaultHeaders): List[SingleResult[A]] = deleteScatterGather(payloads, env)
  def putOneScatterGather[A <: AnyRef](payloads: List[A], env: RequestEnv = getDefaultHeaders): List[SingleResult[A]] = putScatterGather(payloads, env)
  def postOneScatterGather[A <: AnyRef](payloads: List[A], env: RequestEnv = getDefaultHeaders): List[SingleResult[A]] = postScatterGather(payloads, env)

}

