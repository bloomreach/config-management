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
import org.json4s.JsonAST.JValue
import org.json4s.jackson.JsonMethods
import redis.clients.jedis.{BinaryJedis, Jedis, JedisPool, JedisPoolConfig, Pipeline}
import scala.collection.JavaConversions

/**
  * @author amit.kumar
  * 
  * Cache class and companion object
  * will be used to update and read configs from Redis cluster
  * Class with private constructor so that its instantiation is controlled
  * by the companion object
  */
class Cache private(val host: String, val port: Int) extends LazyLogging {

  val Max_Active_Connection = 8
  val jedisPool: JedisPool = {
    val poolConfig = new JedisPoolConfig()
    poolConfig.setMaxActive(Max_Active_Connection)
    new JedisPool(poolConfig, host, port)
  }

  def getJedisConnection(): Jedis = {
    try {
      val jedis = jedisPool.getResource()
      return jedis
    } catch {
      case e: Exception => {
        println("exception in creating jedis connection")
        throw e
      }
    }
  }

  /**
    * @param jedis
    * @param broken
    * releases the jedis resource to pool
    */
  def releaseResource(jedis: BinaryJedis, broken: Boolean) {
    if(broken) {
      jedisPool.returnBrokenResource(jedis)
    } else {
      jedisPool.returnResourceObject(jedis)
    }
  }

  /**
    * adds the set of configs to Cache
    * @param entities
    */
  def add(entities: Set[_ <: CMSEntity]) {
    val jedis: Jedis = getJedisConnection()
    var broken = false
    entities.foreach {
      x: CMSEntity =>
        try {
          jedis.set(x.getRedisKey(), JsonMethods.pretty(x.value))
        } catch {
          case e: Error => {
            logger.error("Couldn't set value for key: " + x.getRedisKey() + " from redis server at host: " + host + " and port: " + port)
            broken = true
            throw e
          }
        } finally {
          releaseResource(jedis, broken)
        }
    }
  }

  /**
    * adds the set of configs to Cache
    * @param entities
    */
  def addToList(entities: Set[_ <: CMSEntity]) {
    val jedis: Jedis = getJedisConnection()
    var broken = false
    entities.foreach {
      x: CMSEntity =>
        try {
          jedis.rpush(x.getRedisKey(), JsonMethods.pretty(x.value))
        } catch {
          case e: Error => {
            logger.error("Couldn't add value for list with key: " + x.getRedisKey() + " from redis server at host: " + host + " and port: " + port)
            broken = true
            throw e
          }
        } finally {
          releaseResource(jedis, broken)
        }
    }
  }
  
  /**
    * @param entity
    */
  def add(entity:  CMSEntity) {
    val jedis = getJedisConnection()
    var broken = false
    try {
      jedis.set(entity.getRedisKey(), JsonMethods.pretty(entity.value))
    } catch {
      case e: Error => {
        logger.error("Couldn't set value for key: " + entity.getRedisKey + " from redis server at host: " + host + " and port: " + port)
        broken = true
        throw e
      }
    } finally {
      releaseResource(jedis, broken)
    }
  }


  /**
    * @param entity
    * @param pipeline
    */
  def addWithPipeline(entity: CMSEntity, pipeline: Pipeline) {
    try {
      pipeline.set(entity.getRedisKey(), JsonMethods.pretty(entity.value))
    } catch {
      case e: Error => {
        logger.error("Couldn't set value for key: " + entity.getRedisKey() + " from redis server at host: " + host + " and port: " + port)
        throw e
      }
    }
  }

  /**
    * @param entity
    * @param pipeline
    */
  def addToListWithPipeline(entity: CMSEntity, pipeline: Pipeline) {
    try {
      pipeline.rpush(entity.getRedisKey(), JsonMethods.pretty(entity.value))
    } catch {
      case e: Error => {
        logger.error("Couldn't set value for key: " + entity.getRedisKey() + " from redis server at host: " + host + " and port: " + port)
        throw e
      }
    }
  }

  /**
    * check if key is present in cache
    * @param key
    */
  def isKeyPresentInCache(key: String): Boolean = {
    val jedis = getJedisConnection()
    var broken = false
    try {
      return jedis.exists(key)
    } catch {
      case e: Error => {
        logger.error("Couldn't check if key : " + key + " exist in redis server at host: " + host + " and port: " + port)
        broken = true
        throw e
      }
    } finally {
      releaseResource(jedis, broken)
    }
  }


  /**
    * @param key
    * @return JValue of the text value of the given key
    */
  def get(key: String): Option[JValue] = {
    val jedis = getJedisConnection()
    var broken = false
    try {
      val value = jedis.get(key)
      if(null != value) {
        return Some(JsonMethods.parse(value))
      } else {
        return None
      }
    } catch {
      case e: java.util.NoSuchElementException => {
        logger.error("Couldn't read value for key: " + key + " from redis server at host: " + host + " and port: " + port)
        broken = true
        return None
      }
      case e: Error => {
        logger.error("Couldn't read value for key: " + key + " from redis server at host: " + host + " and port: " + port)
        broken = true
        throw e
      }
    } finally {
      releaseResource(jedis, broken)
    }
  }
  
  /**
    * @param key
    * @return JValue of the text value of the given key
    */
  def getTextData(key: String): Option[String] = {
    val jedis = getJedisConnection()
    var broken = false
    try {
      val value = jedis.get(key)
      if(null != value) {
        return Some(value)
      } else {
        return None
      }
    } catch {
      case e: java.util.NoSuchElementException => {
        logger.error("Couldn't read value for key: " + key + " from redis server at host: " + host + " and port: " + port)
        broken = true
        return None
      }
      case e: Error => {
        logger.error("Couldn't read value for key: " + key + " from redis server at host: " + host + " and port: " + port)
        broken = true
        throw e
      }
    } finally {
      releaseResource(jedis, broken)
    }
  }


  /**
    * Get all redis key with certain pattern
    * @param pattern
    * @return
    */
  def getAllKeys(pattern: String) : Set[String] = {
    val jedis = getJedisConnection()
    var broken = false

    try {
      return JavaConversions.asScalaSet(jedis.keys(pattern)).toSet
    } catch {
      case e: Error => {
        logger.error("Couldn't read all keys with pattern: " + pattern + " from redis server at host: " + host + " and port: " + port)
        broken = true
        throw e
      }
    }
    finally {
      releaseResource(jedis, broken)
    }
  }

  /**
    * @param oldKey
    * @param newKey
    * @return
    */
  def rename(oldKey: String, newKey: String) : Boolean = {
    val jedis = getJedisConnection()
    var broken = false
    try {
      jedis.renamenx(oldKey, newKey).toInt match {
        case 1 => return true
        case _ => return false
      }
    } catch {
      case e: Error => {
        logger.error("Couldn't rename: " + oldKey + " to " + newKey + " in redis server at host: " + host + " and port: " + port)
        broken = true
        throw e
      }
    } finally {
      releaseResource(jedis, broken)
    }
  }

  /** flush all the keys from cache
    * @return status code
    */
  def flushCache() : String = {
    val jedis = getJedisConnection()
    var broken = false
    try {
      jedis.flushAll()
    } catch {
      case e: Error => {
        logger.error("Couldn't clear cache: in redis server at host: " + host + " and port: " + port)
        broken = true
        throw e
      }
    } finally {
      releaseResource(jedis, broken)
    }
  }
  
  /**
   * @param key
   * @param member
   */
  def addToSet(key: String, member: String) {
    val jedis = getJedisConnection()
    var broken = false
    try {
      jedis.sadd(key, member)
    } catch {
      case e: Error => {
        logger.error("Couldn't add member : " + member + " to set with key: " + key)
        broken = true
        throw e
      }
    } finally {
      releaseResource(jedis, broken)
    }
  }
  
  /**
   * Return the set for a given key
   * @param key
   * @return
   */
  def getMembers(key: String): Set[String] = {
    val jedis = getJedisConnection()
    var broken = false
    try {
      return JavaConversions.asScalaSet(jedis.smembers(key)).toSet
    } catch {
      case e: Error => {
        logger.error("Couldn't read members from set with key: " + key)
        broken = true
        throw e
      }
    } finally {
      releaseResource(jedis, broken)
    }
  }
}


/**
  * @author amit.kumar
  * Companion object for the class Cache
  */
object Cache {
  var cache: Cache = null
  def apply(host: String, port: Int) : Cache = {
    synchronized(Cache)
    if(cache == null) {
      cache = new Cache(host, port)
    }
    cache
  }
}