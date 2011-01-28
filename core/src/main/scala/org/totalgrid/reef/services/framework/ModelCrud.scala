/**
 * Copyright 2011 Green Energy Corp.
 *
 * Licensed to Green Energy Corp (www.greenenergycorp.com) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Green Energy Corp licenses this file
 * to you under the GNU Affero General Public License Version 3.0
 * (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.gnu.org/licenses/agpl.html
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.totalgrid.reef.services.framework

/** 
 * Interface for CRUD model operations
 */
trait ModelCrud[T] {

  /**
   * Create a model entry
   * 
   * @param entry   Object to be created
   * @return        Result of store creation/insertion
   */
  def create(entry: T): T

  /**
   * Update an existing model entry
   * 
   * @param entry       Object to replace existing entry
   * @param existing    Existing entry to be replaced
   * @return            Result stored in data base and whether it was modified
   */
  def update(entry: T, existing: T): (T, Boolean)

  /**
   * Delete an existing entry
   *
   * @param entry       Existing entry to be deleted
   * @return            Result of store delete
   */
  def delete(entry: T): T
}

/** 
 * Hooks/callbacks for modifying behavior without
 *  reimplementing generic CRUD operations
 */
trait ModelHooks[T] {

  /**
   * Called before create 
   * @param entry   Object to be created
   * @return        Verified/modified object
   */
  protected def preCreate(entry: T): T = entry

  /**
   * Called after successful create
   * @param entry   Created entry
   */
  protected def postCreate(entry: T): Unit = {}

  /**
   * Called before update
   * @param entry       Object to replace existing entry
   * @param existing    Existing entry to be replaced
   * @return            Verified/modified object
   */
  protected def preUpdate(entry: T, existing: T): T = entry

  /**
   * Called after successful update
   * @param entry       Updated (current) entry
   * @param previous    Previous entry
   */
  protected def postUpdate(entry: T, previous: T): Unit = {}

  /**
   * Called before delete
   * @param entry       Existing entry to be deleted
   */
  protected def preDelete(entry: T): Unit = {}

  /**
   * Called after successful delete
   * @param previous    Previous entry
   */
  protected def postDelete(previous: T): Unit = {}

  /**
   * checks whether a new entry that is going to override a current entry
   * has actually modified that entry (to avoid unnecssary writes/events and
   * so we can send back correct status code
   */
  def isModified(entry: T, previous: T): Boolean
}

/**
 * Interface for observing model changes
 */
trait ModelObserver[T] {
  protected def onCreated(entry: T): Unit
  protected def onUpdated(entry: T): Unit
  protected def onDeleted(entry: T): Unit
}
