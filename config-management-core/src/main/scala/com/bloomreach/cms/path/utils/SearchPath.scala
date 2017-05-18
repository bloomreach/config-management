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
package com.bloomreach.cms.path.utils

import com.bloomreach.cms.search.FieldMatchingQuery
import com.bloomreach.cms.search.SearchDocument

/**
 * @author amit.kumar
 * SearchPath class is object over a given a string path
 * e.g. /feeds/feed["feed_id"="1"]/feed_settings
 *
 */
class SearchPath(val path: String) {
  val splitPattern = "[\\[.*\\]]"
  
  case class SearchClausePair(from: String, condition: String) {
    /**
     * Create selection set for a query clause
     * e.g. "feed_id"="1" to FieldMatchingQuery(feed_id, 1)
     * @return
     */
    def getSelectionSet(): Set[_ <: FieldMatchingQuery] = {
      if(isSearch()) {
        val queryField = condition.split("=")(0).split("\"").mkString("")
        val queryValue = condition.split("=")(1)
        return Set(FieldMatchingQuery(queryField, queryValue))
      } else{
        // In case of array based indexing we will not have any specific selection criteria
        // No selection criteria means all documents in search result will be selected
        return Set()
      }
    }
    
    /**
      * Checks if it is an array index search or search of fieldmatching
      * ie return true for feed_id="1" and false for 1
      * ( for query cluse is feed[feed_id="1"] and feed[1] respectively )
      *
      * @return
      */
    def isSearch() :Boolean = {
      condition.contains("=")
    }
    
    def filterDocuments(searchDocuments: List[_ <: SearchDocument]): Set[_ <: SearchDocument] = {
      val filteredSearchDocuments = {
        if(isSearch()){
          searchDocuments.toSet
        } else{
          // In case of array based query we will just extract the element out of list
          // -1 for how xquery works
          val document = searchDocuments(condition.toInt - 1)
          Set(document)
        }
      }
      return filteredSearchDocuments
    }
  }
  
  /**
   * @return projectionKey
   * e.g. for /feeds/feed["feed_id"="1"]/feed_settings return feed_settings
   */
  def extractProjectionKey(): Option[String] = {
    val splitedKeyList = getSplittedTokensList
    if (splitedKeyList.last.size == 1) {
      Some(splitedKeyList.last(0))
    } else {
      None
    }
  }
  
  /**
   * @return list of all the search clauses
   * e.g. for /feeds/feed["feed_id"="1"]/feed_settings return List[SearchClausePair(/feeds/feed, "feed_id"="1")]
   */
  def getListOfSearchClauses(): List[SearchClausePair] = {
    val splitedKeyList = getSplittedTokensList

    if (splitedKeyList.last.size == 1) {
      splitedKeyList.dropRight(1).map {x => new SearchClausePair(x(0), x(1))}
    } else {
      splitedKeyList.map {x => new SearchClausePair(x(0), x(1))}
    }
  }
  
  /**
   * for /feeds/feed["feed_id"="1"]/feed_settings
   * @return /feeds/feed
   */
  def getNearestDirectKey(): String = {
    val listOfSearchClauses = getListOfSearchClauses()
    if(listOfSearchClauses.size > 0) {
      //there is at least one search cluase in this search path
      return listOfSearchClauses(0).from
    } else {
      //there is no search clause in this search path
      //so simply return the path
      return this.path
    }
  }
  
  private def getSplittedTokensList(): List[List[String]] = {
    // Splits /feeds/feed["feed_id"="1"]/feed_settings -> Array( /feeds/feed ,"feed_id"="1", feed_settings)
    val splittedPathArray = path.split(splitPattern)
    
    //Array( /feeds/feed ,"feed_id"="1", feed_settings) -> List[List[/feeds/feed, "feed_id"="1"], List[feed_settings]]
    val splitedKeyList = splittedPathArray.toList.grouped(2).toList

    return splitedKeyList
  }
}