/**
 * Copyright 2016 BloomReach, Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.bloomreach.cms.redis

import org.json4s.JsonAST.{JNothing, JValue}

/**
  * Created by neel on 4/28/16.
  * This CMSEntity class represents any entity that can be stored in redis by this library
  * CMSConfig represents actual configuration to be stored
  * CMSTemplate represents any template (schema) to be stored
  * 
  */

abstract class CMSEntity(val value: JValue) {
  /**
    * Creates and returns redis cluster key
    *
    * @return
    */
  def getRedisKey(): String
}

case class CMSConfig(val realm: String,
                     val dbName: String,
                     val documentName: String,
                     val key: String,
                     override val value: JValue = JNothing) extends CMSEntity(value) {


  def getRedisKey(): String = {
    val redisKey = dbName + "/" + documentName + "/" + realm + "/" + key
    return redisKey
  }
}


case class CMSTemplate(val dbName: String,
                       val templateName: String,
                       override val value: JValue = JNothing) extends CMSEntity(value) {

  def getRedisKey(): String = {
    return s"templates/$dbName/$templateName"
  }
}
