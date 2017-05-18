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

import com.typesafe.scalalogging.LazyLogging
import com.bloomreach.cms.search.SearchUtils
import com.bloomreach.cms.path.utils.TemplateUtils
import org.json4s.JsonAST.JObject
import org.json4s.JsonAST.{JValue, JField}
import org.json4s.jackson.JsonMethods
import com.bloomreach.cms.path.utils.Utils

/**
 * @author amit.kumar
 * This class is to retrieve configs, templates from cms cache
 */
class CacheReader(val host: String, val port: Int = 6379) extends LazyLogging  {
  val cache: Cache = Cache(host, port)
  
  def getDocumentIdForDbRealm(db: String, realm: String) = {
    s"dbName/$db/realm/$realm/documentID"
  }
  
  def getRealmKeyForDB(db: String): String = {
    s"dbName/$db/realms"
  }

  def getDocuemntKeyForDbRealm(db: String, realm: String) = {
    s"dbName/$db/realm/$realm/documentName"
  }

  /**
    *
    * @param dbName
    * @return
    */
  def getAllDocumentNames(dbName: String) : Set[String] = {
    getAllRealmForDb(dbName).flatMap(getAllDocumentNamesForRealm(dbName, _))
  }
  
  /**
    *
    * @param dbName
    * @return
    */
  def getAllDocumentIDs(dbName: String) : Set[String] = {
    getAllRealmForDb(dbName).flatMap(getAllDocumentIDsForRealm(dbName, _))
  }

  /**
    *
    * @param dbName
    * @return
    */
  def getAllDocumentNamesForRealm(dbName: String, realm: String) : Set[String]= {
    try {
      return cache.getMembers(getDocuemntKeyForDbRealm(dbName,realm))
    } catch {
      // It will return empty set when no document is present in the realm
      case e: Exception => return Set()
    }
  }

  /**
    *
    * @param dbName
    * @return
    */
  def getAllDocumentIDsForRealm(dbName: String, realm: String) : Set[String]= {
    try {
      return cache.getMembers(getDocumentIdForDbRealm(dbName, realm))
    } catch {
      // It will return empty set when no document is present in the realm
      case e: Exception => return Set()
    }
  }
  
  /**
   * read all the available realms for a given db from redis
   *
   * @param dbName
   * @return
   */
  def getAllRealmForDb(dbName: String): Set[String] = {                                                                
    if (getAllDb.contains(dbName)) {                                                                                                  
      return cache.getMembers(getRealmKeyForDB(dbName))
    } else {                                                                                                                           
      throw new NoSuchElementException
    }
  }
                                                                                                                                       
  /**
   * Return all the DbNames stored in redis cache
    *
    * @return
   */
  def getAllDb : Set[String]= {                                                                                                      
    return cache.getMembers(Constants.DB_NAME_KEY)                                                                                                          
  }
  
  /**
    *
    * @param dbName
    * @param templateName
    * @return
    */
  def getTemplate(dbName: String, templateName: String): JValue = {
    val cmsTemplate = CMSTemplate(dbName, templateName)

    cache.get(cmsTemplate.getRedisKey()) match {
      case None => {
        logger.error(s" ${cmsTemplate.getRedisKey()} template not yet inserted")
        throw new NoSuchElementException
      }
      case Some(x) => x
    }
  }
  
  /**
    *
    * @param dbName
    * @param templateName
    * @return
    */
  def getTemplateText(dbName: String, templateName: String): Option[String] = {
    val cmsTemplate = CMSTemplate(dbName, templateName)

    cache.getTextData(cmsTemplate.getRedisKey()) match {
      case None => {
        logger.error(s" ${cmsTemplate.getRedisKey()} template not yet inserted")
        throw new NoSuchElementException
      }
      case Some(x) => Some(x)
    }
  }
  
  /**
    *
    * @param dbName
    * @param templateName
    * @return
    */
  def getTemplateTextForKey(dbName: String, templateName: String, key: String): Option[String] = {
    logger.info("received key for template extraction: " + key)
    try {
      var currentTemplate = getTemplate(dbName, templateName)
      var currentTemplateIsArray = false
      val fullPathArray = key.split("/")
      fullPathArray.foreach { 
        x => {
          //extract the element inside the items key if current template is an array
          if(currentTemplateIsArray) {
            currentTemplate = currentTemplate \ TemplateUtils.Items
          }
          
          if(SearchUtils.isKeyAnArrayIndex(x) || SearchUtils.isKeyArraySearchQuery(x)) {
            val validKey = {
              if(SearchUtils.isKeyAnArrayIndex(x)) SearchUtils.getArrayNameAndIndex(x)._1
              else SearchUtils.getArrayNameAndQuery(x)._1
            }
            currentTemplate = currentTemplate \ TemplateUtils.Properties
            currentTemplate = currentTemplate \ validKey
            currentTemplateIsArray = true
          } else {
            currentTemplate = currentTemplate \ TemplateUtils.Properties
            currentTemplate = currentTemplate \ x
            currentTemplateIsArray = false
            if(currentTemplate.asInstanceOf[JObject].values.keySet.contains(TemplateUtils.Items)) {
              currentTemplateIsArray = true
            }
          }
        }
      }
      return Some(JsonMethods.pretty(currentTemplate))
    } catch {
      case e: Exception => {
        logger.error(s"Invalid key passed for template extraction, key: $key")
        return None
      }
    }
  }
  
  /**
   * @param cmsConfig
   * @return defaultValues for a cmsConfig
   */
  def getDefaultValues(cmsConfig: CMSConfig): Option[JValue] = {
    val redisKey = cmsConfig.getRedisKey()
    val schema = getTemplateTextForKey(cmsConfig.dbName, Constants.UBER_SCHEMA_NAME, cmsConfig.key)
    val keyName = Utils.getKeyNameFromPath(redisKey)
    val validKeyName = {
      if(SearchUtils.isKeyAnArrayIndex(keyName)) {
        SearchUtils.getArrayNameAndIndex(keyName)._1
      } else if(SearchUtils.isKeyArraySearchQuery(keyName)){
        SearchUtils.getArrayNameAndQuery(keyName)._1
      } else {
        keyName
      }
    }
    val defaultValues = schema match {
      case Some(x) => {
        val schemaJson = JObject(List(JField(validKeyName, JsonMethods.parse(x))))
        val defaultValues = TemplateUtils.getConfigWithDefaultValues(schemaJson)
        Some(defaultValues)
      }
      case _ => None
    }
    return defaultValues
  }
}