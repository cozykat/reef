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
package org.totalgrid.reef.api.service

import org.totalgrid.reef.api.RequestEnv
import org.totalgrid.reef.api.scalaclient.Response

trait AsyncToSyncServiceAdapter[A] extends AsyncServiceBase[A] {

  def get(req: A, env: RequestEnv): Response[A]
  def put(req: A, env: RequestEnv): Response[A]
  def delete(req: A, env: RequestEnv): Response[A]
  def post(req: A, env: RequestEnv): Response[A]

  final def get(req: A): Response[A] = get(req, new RequestEnv)
  final def put(req: A): Response[A] = put(req, new RequestEnv)
  final def delete(req: A): Response[A] = delete(req, new RequestEnv)
  final def post(req: A): Response[A] = post(req, new RequestEnv)

  override def getAsync(req: A, env: RequestEnv)(callback: Response[A] => Unit) = callback(get(req, env))
  override def putAsync(req: A, env: RequestEnv)(callback: Response[A] => Unit) = callback(put(req, env))
  override def deleteAsync(req: A, env: RequestEnv)(callback: Response[A] => Unit) = callback(delete(req, env))
  override def postAsync(req: A, env: RequestEnv)(callback: Response[A] => Unit) = callback(post(req, env))

}