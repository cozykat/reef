package org.totalgrid.reef.japi.client;

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

import org.totalgrid.reef.japi.ReefServiceException;
import org.totalgrid.reef.japi.ServiceIOException;
import org.totalgrid.reef.japi.TypeDescriptor;

/**
 *  The interface that a concrete service client must provide.
 */
public interface Session {

  /* -------- Synchronous API ------------ */

  <A> Promise<Response<A>> get(A payload) throws ReefServiceException;
  <A> Promise<Response<A>> delete(A payload) throws ReefServiceException;
  <A> Promise<Response<A>> post(A payload) throws ReefServiceException;
  <A> Promise<Response<A>> put(A payload) throws ReefServiceException;


  <A> Promise<Response<A>> get(A payload, Subscription<A> subscription) throws ReefServiceException;
  <A> Promise<Response<A>> delete(A payload, Subscription<A> subscription) throws ReefServiceException;
  <A> Promise<Response<A>> post(A payload, Subscription<A> subscription) throws ReefServiceException;
  <A> Promise<Response<A>> put(A payload, Subscription<A> subscription) throws ReefServiceException;

  /* --- Misc --- */

  <A> Subscription<A> addSubscription(TypeDescriptor<A> descriptor) throws ServiceIOException;

  <A> Subscription<A> addSubscription(TypeDescriptor<A> descriptor, SubscriptionEventAcceptor<A> acceptor) throws ServiceIOException;

  ServiceHeaders getDefaultHeaders();

  void close();

}
