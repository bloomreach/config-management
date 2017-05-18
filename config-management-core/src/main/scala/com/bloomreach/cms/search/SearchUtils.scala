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

import com.bloomreach.cms.redis.CMSConfig

/**
  * @author neel.choudhury
  */
object SearchUtils {
  def isValidStringToken(s: String): Boolean = {

    def isInt:Boolean = {
      try {
         s.toInt
        return true
      }
      catch{
        case e: Exception =>return false
      }
    }

    def isFloat:Boolean = {
      try {
        s.toFloat
        return true
      }
      catch{
        case e: Exception =>return false
      }
    }

    def isBoolean:Boolean = {
      try {
        s.toBoolean
        return true
      }
      catch{
        case e: Exception =>return false
      }
    }

    def isSymbol:Boolean = {
      ! s.map(Character.isLetterOrDigit(_)).fold(false)(_ || _)
    }

    def containJsonSymbol: Boolean = {
      s.contains("{") || s.contains("}") || s.contains("[") || s.contains("]") || s.contains(":") || s.contains(",")
    }

    return ! ( isInt || isFloat || isBoolean || isSymbol || containJsonSymbol)
  }

  def addQuote(s: String): String = {

    // We only add quote if not already present
    // We also don't add quotes if one quote is present on either side
    // (as the string can be splitted inside a string)
    if(s.last.toString == "\"" || s(0).toString == "\"") {
      return s
    } else {
      return "\"" + s + "\""
    }
  }

  /**
    * Following function adds quotes for all string literal in a json
    * (Those already having " is left behind
    * For eg {arr:[1,true,kk,"ll"]} is converted to {"arr":[1,true,"kk","ll"]}
    *
    * @param s (A string)
    * @return
    */
  def quoteStringLiteralsInJson(s: String): String = {

    // The regular expression uses json symbols << , { } [ ] >> to split
    // and uses look ahead and look behind to avoid "

    val REGEX_SPLITING_STRING = "(?=[{}:\\[\\],])|(?<=[{}:\\[\\],])"
    val quotedArray = s.split(REGEX_SPLITING_STRING).map( { x =>

      if(isValidStringToken(x.trim))
        addQuote(x.trim)
      else
        x.trim

    } ).toList

    return quotedArray.mkString("")
  }

  def time[R](block: => R, msg: String =""): R = {
    val t0 = System.currentTimeMillis()
    val result = block    // call-by-name
    val t1 = System.currentTimeMillis()

    result
  }
  
  /**
   * @param value
   * @return boolean value for the string representation
   */
  def getBooleanValueForString(value: String): Boolean = {
    //In case of 0/1 we need to change them to true false for checking
    if(value == "true" || value == "false") {
      return value.toBoolean
    } else if(value == "0") {
      false
    } else if(value == "1") {
      true
    } else { // In case of neither of them return false
      return false
    }
  }
  
  /**
    * Checks if it is an array search for fieldmatching
    * ie return true for feed_id="1" and false for 1
    *
    * @param queryClause
    * @return
    */
  def isKeyArraySearchQuery(key: String) :Boolean = {
    try {
      if(key.contains("[") && key.contains("]")) {
        val openningBracketIndex = key.indexOf("[")
        val closingBracketIndex = key.indexOf("]")
        val query = key.substring(openningBracketIndex + 1 , closingBracketIndex)
        return (openningBracketIndex < closingBracketIndex) && query.contains("=")
      } else {
        return false
      }
    } catch {
      case _ : Error | _ : Exception => {
        return false
      }
    }
  }
  
  /**
   * @param key
   * @return index of arrayName and query
   * for feed[feed_id="1"] return (feed, feed_id="1")
   */
  def getArrayNameAndQuery(key: String): (String, String) = {
    val openningBracketIndex = key.indexOf("[")
    val closingBracketIndex = key.indexOf("]")
    val query = key.substring(openningBracketIndex + 1 , closingBracketIndex)
    val arrayName = key.substring(0, openningBracketIndex)
    return (arrayName, query)
  }
  
  /**
   * @param key
   * @return true if key is of pattern str[{number}]
   * else return false
   */
  def isKeyAnArrayIndex(key: String): Boolean = {
    try {
      if(key.contains("[") && key.contains("]")) {
        val openningBracketIndex = key.indexOf("[")
        val closingBracketIndex = key.indexOf("]")
        val index = key.substring(openningBracketIndex + 1 , closingBracketIndex)
        val indexInteger = index.toInt
        return openningBracketIndex < closingBracketIndex
      } else {
        return false
      }
    } catch {
      case _ : Error | _ : Exception => {
        return false
      }
    }
  }
  
  /**
   * @param key
   * @return index of array from pattern str[{number}]
   */
  def getArrayNameAndIndex(key: String): (String, Int) = {
    val openningBracketIndex = key.indexOf("[")
    val closingBracketIndex = key.indexOf("]")
    val index = key.substring(openningBracketIndex + 1 , closingBracketIndex)
    val arrayName = key.substring(0, openningBracketIndex)
    //decrease index by one because in xPath format index starts with 1
    //so in the key requested the index is 1 + normal index
    return (arrayName, index.toInt - 1)
  }
  
  def getKeyForArrayPattern(token: String): String = {
    if(isKeyAnArrayIndex(token)) {
      getArrayNameAndIndex(token)._1
    } else {
      getArrayNameAndQuery(token)._1
    }
  }
  
  /**
   * Retruns all the lazy documents from the universe of documents
   * @param universeOfDocument
   * @return
   */
  def getAllLazyDocuments(universeOfDocument: Set[_ <: SearchDocument]): Set[_ <: com.bloomreach.cms.search.SearchDocument] = {
    val allLazyDocuments = universeOfDocument.filter { 
      document => {
        document match {
          case lazyDocument: LazyDocument => true
          case fullDocument: FullDocument => false
        }
      }
    }
    return allLazyDocuments
  }
  
  /**
   * Creates unique key to CMSConfig map
   * key will be of pattern dbName/realm/config/key1/key2
   * (no document name in the key, so that it can be used for fetching default value for the actual key)
   * @param allLazyDocuments
   * @param fieldName
   * @return
   */
  def getUniqueKeyToConfigMapForLazyDocuments(allLazyDocuments: Set[LazyDocument], fieldName: String): Map[String, CMSConfig] = {
    val uniqueKeyToConfigMap = allLazyDocuments.map { 
      lazyDocument => {
        val searchCMSConfig = CMSConfig(lazyDocument.cmsConfig.realm, lazyDocument.cmsConfig.dbName, lazyDocument.cmsConfig.documentName, fieldName)
        //As these configs are required for default value extraction
        (List(lazyDocument.cmsConfig.dbName, lazyDocument.cmsConfig.realm, fieldName).mkString("/") -> searchCMSConfig)
      }
    }.toMap
    
    return uniqueKeyToConfigMap
  }
}

