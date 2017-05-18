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
package com.bloomreach.cms.search

import com.bloomreach.cms.redis.{CMSConfig, CacheUpdater, CacheReader}
import org.json4s.JsonAST.{JField, JObject, JValue, JNothing}
import com.typesafe.scalalogging.LazyLogging
import org.json4s.jackson.JsonMethods
import com.bloomreach.cms.redis.CMSConfig
import com.bloomreach.cms.path.utils.Utils
import com.bloomreach.cms.path.utils.TemplateUtils
/**
  * @author neel.choudhury
  */
abstract class SearchDocument extends LazyLogging {
  def getValueForKey(key: String) : Option[JValue]
}

/**
  * For full document we get the data for the entire document during initialization
  * @param data
  */
case class FullDocument(val data: JValue) extends SearchDocument {

  /**
    * In case of full document we get value for a key using methods of json4s
    * @param keyPath
    * @return
    */
  override def getValueForKey(keyPath: String) : Option[JValue]= {

    val key = Utils.getKeyNameFromPath(keyPath)
    val res = data \\ key
    res match {
      case JObject(List()) => {
        return None
      }
      case _ => {
        return Some(JObject(List(JField(key, res))))
      }
    }
  }
}

/**
  * For lazy documents we keep a reference of the document
  * @param cmsConfig
  * @param cacheUpdater
  */
case class LazyDocument(val cmsConfig: CMSConfig, val cacheUpdater: CacheUpdater, cacheReader: CacheReader) extends SearchDocument {

  /**
    * In case of lazy document we get the value for a key from redis
    * @param keyPath
    * @return
    */
  override def getValueForKey(keyPath: String) : Option[JValue]= {
    val searchCmsConfig = CMSConfig(cmsConfig.realm, cmsConfig.dbName, cmsConfig.documentName, keyPath)
    val defaultValue = Utils.time(cacheReader.getDefaultValues(searchCmsConfig), "time taken to fetch default value")
    return getValueForKey(defaultValue, keyPath)
  }
  
  /**
    * In case of lazy document we get the value for a key from redis
    * @param keyPath
    * @return
    */
  def getValueForKey(defaultValue: Option[JValue], keyPath: String) : Option[JValue]= {
    val searchCmsConfig = CMSConfig(cmsConfig.realm, cmsConfig.dbName, cmsConfig.documentName, keyPath)
    val concreteValue = cacheUpdater.getNestedValue(searchCmsConfig.getRedisKey())
    val mergedValue = (defaultValue, concreteValue) match {
      case (Some(d), Some(c)) => Utils.time(TemplateUtils.mergeDefaultValueWithConcreteValue(d, c), "time taken by mergeTwoJson method: ")
      case _ => None
    }
    
    return mergedValue
  }
}