package org.constellation.lb

import cats.data.{NonEmptyMap, NonEmptySet}

import scala.collection.immutable.{SortedMap, SortedSet}

object NemOListOps {

  /**
    * Extract new keys from the map entries
    * @param nem map of option lists
    * @param v2k value to key
    * @tparam K
    * @tparam V
    * @return new keys
    */
  def extractNewKeys[K: Ordering, V](nem: NonEmptyMap[K, Option[List[V]]])(v2k: V => K): SortedSet[K] =
    nem.toNel.foldLeft(SortedSet.empty[K])(
      (acc, bcc) =>
        bcc match {
          case (_, Some(hosts)) => acc ++ hosts.map(v2k).filterNot(nem(_).isDefined)
          case _                => acc
        }
    )

  /**
    * Split a map of entries into two maps: one with the entries where all elements of the list satisfy a property and one when not
    * @param map map of option lists
    * @param prop property that all elements of the list must satisfy
    * @tparam K
    * @tparam V
    * @return (map of elements that satisfy the property, map of elements that don't)
    */
  def splitByElementProp[K, V](map: Map[K, List[V]])(prop: V => Boolean): (Map[K, List[V]], Map[K, List[V]]) =
    map.partition { case (_, v) => v.forall(prop) }

  /**
    * Finds the first element of the lists in the map that satisfies a property
    * @param map map of option lists
    * @tparam K
    * @tparam V
    * @return first element wrapped in Some if found or None
    */
  def findFirstElement[K, V](map: Map[K, Option[List[V]]]): Option[V] = {
    map.collectFirst { case (_, Some(e :: _)) => e }
  }

  /**
    * Returns a map with only the keys specified in validKeys
    *
    * @param validKeys sets with keys to keep
    * @param map map to filter
    * @tparam K keys
    * @tparam V values
    * @return filtered map
    */
  def filterKeys[K, V](validKeys: NonEmptySet[K], map: NonEmptyMap[K, V]): SortedMap[K, V] =
    map.toSortedMap.filter { case (k, _) => validKeys.contains(k) }

  /**
    * Builds a peer map grouping the peer info by each address
    * @param clusterInfo cluster info
    * @return map by address of peers
    */
  /**
    * "matrix-like" rotation of the map, creating a new map based regrouping by the keys extracted from the entry lists.
    * @param map map of option lists
    * @param v2k value to key
    * @tparam K
    * @tparam V
    * @return map with lists from entries.
    */
  def rotateMap[K, V](map: NonEmptyMap[K, Option[List[V]]])(v2k: V => K): Map[K, List[V]] =
    map.toNel
      .collect {
        case (_, Some(el)) => el
      }
      .flatten
      .groupBy(v2k)

}
