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
package org.totalgrid.reef.api.javaclient

import org.totalgrid.reef.api.scalaclient.ClientSessionPool
import org.totalgrid.reef.api.ReefServiceException

trait ISessionPool {

  /**
   * Executes a block of code using a temporarily acquired session cleaning up any affected state afterwards
   * @consumer a block of code to execute using the acquired ISession
   * @return the return value from consumer.apply
   * @throws ServiceIOException if a session cannot be acquired we will throw an error
   */
  @throws(classOf[ReefServiceException])
  def borrow[A](consumer: ISessionConsumer[A]): A

  /**
   * Executes a block of code using a temporarily acquired session cleaning up any affected state afterwards
   * @param consumer a block of code to execute using the acquired ISession
   * @param authToken an authtoken to attach before calling consumer.apply
   * @return the return value from consumer.apply
   * @throws ServiceIOException if a session cannot be acquired we will throw an error
   * @throws ReefServiceException
   */
  @throws(classOf[ReefServiceException])
  def borrow[A](consumer: ISessionConsumer[A], authToken: String): A

  def getUnderlyingClientSessionPool: ClientSessionPool
}