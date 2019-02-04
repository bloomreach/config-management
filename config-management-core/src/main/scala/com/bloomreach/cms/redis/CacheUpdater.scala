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

import org.json4s.JsonAST._
import org.json4s.jackson.JsonMethods
import com.bloomreach.cms.search.SearchUtils
import com.typesafe.scalalogging.LazyLogging
import redis.clients.jedis.Pipeline
import com.bloomreach.cms.path.utils.SearchPath
import org.apache.commons.io.FileSystemUtils
import com.bloomreach.cms.path.utils.SearchPath
import com.bloomreach.cms.path.utils.Utils
import com.bloomreach.cms.path.utils.TemplateUtils

/**
  * @author amit.kumar
  * CacheUpdater object to provide operations on the cache
  * which are not provided by redis directly
  */
class CacheUpdater(val host: String, val port: Int = 6379) extends LazyLogging {
  val cache: Cache = Cache(host, port)
  val cacheReader: CacheReader = new CacheReader(host, port)
  
  /**
   * Add dbName to the set with key DB_NAME_KEY
    *
    * @param dbName
   */
  def addDbName(dbName: String) {
    cache.addToSet(Constants.DB_NAME_KEY, dbName)
  }
  
  /**
   * @param dbName
   * @param realm
   */
  def addRealmForDB(dbName: String, realm: String) {
    cache.addToSet(cacheReader.getRealmKeyForDB(dbName), realm)
  }
  
  def addDocumentFor(dbName: String, realm: String, documentName: String) {
    val setName = cacheReader.getDocuemntKeyForDbRealm(dbName, realm)
    cache.addToSet(setName, documentName)
    logger.info("Added document name : " + documentName + " to the set " + setName)
  }
  
  def addDocumentIDFor(dbName: String, realm: String, documentID: Int) {
    val setName = cacheReader.getDocumentIdForDbRealm(dbName, realm)
    cache.addToSet(setName, documentID.toString())
    logger.info("Added document id : " + documentID + " to the set " + setName)
  }
  
  /**
    *
    * @param dbName
    * @param templateName
    * @param value
    */
  def addTemplate(dbName: String, templateName: String, value: JValue) {
    val cmsTemplate = CMSTemplate(dbName, templateName, value)
    cache.add(cmsTemplate)
  }
  
  /**
    * @param realm
    * @param dbName
    * @param documentName
    * @param key
    * @param value
    */
  def updateRecursively(realm: String, dbName: String, documentName: String, key: String, value: String) {
    // jedis pipeline to perform transactional updates in redis
    val jedis = cache.getJedisConnection()
    val jedisPipeline = jedis.pipelined()
    var broken = false
    try {
      jedisPipeline.multi()
      updateParentsRecursivelyWithPipeline(realm, dbName, documentName, key, JsonMethods.parse(value), jedisPipeline)
      updateChildrenRecursivelyWithPipeline(realm, dbName, documentName, key, JsonMethods.parse(value), jedisPipeline)
      jedisPipeline.exec()
      jedisPipeline.sync()
    } catch {
      case e: Error => {
        logger.error("could not update the key " + key + " to redis cluster with host: " +
                       cache.host + " port " + cache.port)
        broken = true
      }
    } finally {
      cache.releaseResource(jedis, broken)
    }
  }
  
  /**
   * updates a child inside a parent json and returns the updated value
    *
    * @param parentKey
   * @param parentJsonValue
   * @param keyTobeUpdated
   * @param newValue
   * @return
   */
  def getUpdatedParentJson(parentKey: String, parentJsonValue: Option[JValue], keyTobeUpdated: String, newValue: JValue) : JObject = {
    val updatedParentJson =  parentJsonValue match {
      //parent key is present in cache, so value of child will be updated in it
      case Some(x) => {
        //first remove the current child
        val filteredParentJson = x removeField {
          case JField(s, _: JValue) if s == keyTobeUpdated => true
          case _ => false
        }
        //now add the new child with updated value
        JObject((filteredParentJson \ parentKey).asInstanceOf[JObject].obj :+ JField(keyTobeUpdated, newValue))
      }
      //parent key is not present in cache, so will be fresh inserted
      case _ => {
        JObject(JField(keyTobeUpdated, newValue))
      }
    }
    return updatedParentJson
  }

  /**
    * @param realm
    * @param dbName
    * @param documentName
    * @param key
    * @param value
    * @param jedisPipeline
    */
  def updateParentsRecursivelyWithPipeline(realm: String, dbName: String, documentName: String,
                                           key: String, value: JValue, jedisPipeline: Pipeline) {
    val prefix = dbName + "/" + documentName + "/" + realm + "/"
    var currentKey = key
    var currentKeyName = Utils.getKeyNameFromPath(currentKey)
    var currentJsonValue = value \ currentKeyName
    while(currentKey != null) {
      visitConfig(realm, dbName, documentName, currentKey, JObject(JField(currentKeyName, currentJsonValue)), jedisPipeline)
      val parentKeyOption = Utils.getParentKey(currentKey)
      parentKeyOption match {
        case Some(parentKey) => {
          val parentJsonValue = cache.get(prefix + parentKey)
          val parentKeyName = Utils.getKeyNameFromPath(parentKey)
          val updatedParentJson = getUpdatedParentJson(parentKeyName, parentJsonValue, currentKeyName, currentJsonValue)
          
          //now update the loop variables
          currentJsonValue = updatedParentJson
          currentKey = parentKey
          currentKeyName = parentKeyName
        }
        case _ => {
          currentKey = null
        }
      }
    }
  }

  /**
    * update children of the current key to redis
 *
    * @param realm
    * @param dbName
    * @param documentName
    * @param key
    * @param value
    * @param jedisPipeline
    */
  def updateChildrenRecursivelyWithPipeline(realm: String, dbName: String, documentName: String,
                                            key: String, value: JValue, jedisPipeline: Pipeline) {
    val prefix = dbName + "/" + documentName + "/" + realm + "/"
    var currentKey = key
    var currentKeyName = Utils.getKeyNameFromPath(currentKey)
    var currentJsonValue = value
    val children = (currentJsonValue \ currentKeyName)
    children match {
      case x: JObject => {
        val childrenKeys = x.values.keySet
        for(childKey <- childrenKeys) {
          val childValue = children \ childKey
          visitConfig(realm, dbName, documentName, currentKey + "/" + childKey, JObject(JField(childKey, childValue)), jedisPipeline)
          updateChildrenRecursivelyWithPipeline(realm, dbName, documentName, currentKey + "/" + childKey, children, jedisPipeline)
        }
      }
      case x: JArray => {
        for(child <- x.arr) {
          visitArrayElement(realm, dbName, documentName, key, child, jedisPipeline)
        }
      }
      case _ => {
        logger.error("Neither an object nor an array, should not reach this case")
      }
    }
  }

  /**
    * find the value of a key recursively
    * First finds the nearest ancestor for which vlaue is available in redis
    * Then extracts the value for this key from the parent value
    *
    * @param key
    * @return
    */
  def getNestedValue(key: String): Option[JValue] = {
    try{
      val value = cache.get(key)
      val nestedValue = value match {
        case Some(x) => Some(x)
        case _ => {
          var nestedParentValue: Option[JValue] = None
          var currentKey = key
          var parentKey = Utils.getParentKey(key)
          //traverse upwards in hierarchy until you find a parent with some value
          while(parentKey != None && nestedParentValue == None) {
            parentKey match {
              case Some(y) => nestedParentValue = cache.get(y)
              case _ => nestedParentValue = None
            }
            currentKey = parentKey.get
            parentKey = Utils.getParentKey(currentKey)
          }
          //We either found the nearest ancestor which has value in redis cluster
          // or reached the root in hierarchy, means wrong key
          //Now go down in hierarchy, one level at a time
          nestedParentValue match {
            case Some(v) => {
              val splitedList = key.split("/")
              var currentKeyName = Utils.getKeyNameFromPath(currentKey)
              var currentValue = Some(nestedParentValue.get \ currentKeyName)
              for(i <- splitedList.indexOf(currentKeyName) + 1 to splitedList.length -1) {
                currentKeyName = splitedList(i)
                if(SearchUtils.isKeyAnArrayIndex(currentKeyName)) {
                  val (arrayName, index) = SearchUtils.getArrayNameAndIndex(currentKeyName)
                  currentValue = Some(((currentValue.get) \ arrayName).asInstanceOf[JArray].arr(index))
                } else {
                  currentValue = Some((currentValue.get) \ currentKeyName)
                }
              }
              val leafKey = Utils.getKeyNameFromPath(key)
              val leafKeyName = {
                if(SearchUtils.isKeyAnArrayIndex(leafKey)) {
                  val (name, index) = SearchUtils.getArrayNameAndIndex(leafKey)
                  name
                } else {
                  leafKey
                }
              }

              if(currentValue.get == JNothing) {
                None
              } else {
                Some(JObject(List(JField(leafKeyName, currentValue.get))))
              }
            }
            case _ => None
          }
        }
      }
      return nestedValue
    } catch {
      case _: Exception => {
        logger.error("Bad URL: " + key)
        return None
      }
    }
  }

  /**
    * visit method to simply add a given config
    *
    * @param realm
    * @param dbName
    * @param documentName
    * @param key
    * @param value
    * @param pipeline
    */
  def visitConfig(realm: String, dbName: String, documentName: String, key: String, value: JValue, pipeline: Pipeline) {
    val leafConfig: CMSConfig = new CMSConfig(realm, dbName, documentName, key, value)
    cache.addWithPipeline(leafConfig, pipeline)
  }

  /**
    * visit method to simply add a given config
 *
    * @param realm
    * @param dbName
    * @param documentName
    * @param key
    * @param value
    * @param pipeline
    */
  def visitArrayElement(realm: String, dbName: String, documentName: String, key: String, value: JValue, pipeline: Pipeline) {
    val leafConfig: CMSConfig = new CMSConfig(realm, dbName, documentName, key, value)
    cache.addToListWithPipeline(leafConfig, pipeline)
  }
                                                                                                                                  

  
  /**
   * flush the associated cache
   */
  def flushCache() {
    cache.flushCache()
  }
}
