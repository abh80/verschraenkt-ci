package com.verschraenkt.ci.storage.repository

import cats.effect.IO
import com.verschraenkt.ci.storage.context.StorageContext
import com.verschraenkt.ci.storage.db.PostgresProfile.MyAPI.{ cacheScopeTypeMapper, cacheStatusMapper }
import com.verschraenkt.ci.storage.db.codecs.Enums.{ CacheScopeType, CacheStatus }
import com.verschraenkt.ci.storage.db.tables.{ CacheEntryInsert, CacheEntryRow, CacheEntryTable }
import com.verschraenkt.ci.storage.db.{ DatabaseModule, PostgresProfile }

import java.time.Instant
import java.util.UUID

sealed trait ICacheEntryRepository:
  /** Create a new cache entry, returning the generated cache ID */
  def create(entry: CacheEntryInsert): IO[UUID]

  /** Find a cache entry by ID */
  def findById(cacheId: UUID): IO[Option[CacheEntryRow]]

  /** Look up a ready cache entry by key, scope, and hashes (cache hit) */
  def findByKeyAndScope(
      cacheKey: String,
      scopeType: CacheScopeType,
      scopeValue: Option[String],
      pathsHash: String,
      inputHash: String
  ): IO[Option[CacheEntryRow]]

  /** Find all cache entries for a given key and scope */
  def findByKey(
      cacheKey: String,
      scopeType: CacheScopeType,
      scopeValue: Option[String]
  ): IO[Seq[CacheEntryRow]]

  /** Update cache status */
  def updateStatus(cacheId: UUID, status: CacheStatus): IO[Boolean]

  /** Update content hash (typically when transitioning from Creating to Ready) */
  def updateContentHash(cacheId: UUID, contentHash: String): IO[Boolean]

  /** Record a cache access (increments access_count and updates last_accessed_at) */
  def recordAccess(cacheId: UUID): IO[Boolean]

  /** Find expired cache entries */
  def findExpired(at: Instant = Instant.now(), limit: Int = 100): IO[Seq[CacheEntryRow]]

  /** Find least recently accessed entries for eviction */
  def findLeastRecentlyAccessed(limit: Int = 100): IO[Seq[CacheEntryRow]]

  /** Delete a cache entry by ID */
  def delete(cacheId: UUID): IO[Boolean]

class CacheEntryRepository(val dbModule: DatabaseModule)
    extends ICacheEntryRepository
    with DatabaseOperations
    with StorageContext:
  import PostgresProfile.api.*

  private val table = TableQuery[CacheEntryTable]

  override def create(entry: CacheEntryInsert): IO[UUID] =
    withContext("create") {
      val insertAction = table.map(_.forInsert).returning(table.map(_.cacheId)) += entry
      transactionally(insertAction)
    }

  override def findById(cacheId: UUID): IO[Option[CacheEntryRow]] =
    withContext("findById") {
      val query = table
        .filter(_.cacheId === cacheId)
        .result
        .headOption

      run(query)
    }

  override def findByKeyAndScope(
      cacheKey: String,
      scopeType: CacheScopeType,
      scopeValue: Option[String],
      pathsHash: String,
      inputHash: String
  ): IO[Option[CacheEntryRow]] =
    withContext("findByKeyAndScope") {
      val baseQuery = table
        .filter(_.cacheKey === cacheKey)
        .filter(_.scopeType === scopeType)
        .filter(_.pathsHash === pathsHash)
        .filter(_.inputHash === inputHash)
        .filter(_.status === (CacheStatus.Ready: CacheStatus))

      val query = scopeValue match
        case Some(sv) => baseQuery.filter(_.scopeValue === sv)
        case None     => baseQuery.filter(_.scopeValue.isEmpty)

      run(query.result.headOption)
    }

  override def findByKey(
      cacheKey: String,
      scopeType: CacheScopeType,
      scopeValue: Option[String]
  ): IO[Seq[CacheEntryRow]] =
    withContext("findByKey") {
      val baseQuery = table
        .filter(_.cacheKey === cacheKey)
        .filter(_.scopeType === scopeType)

      val query = scopeValue match
        case Some(sv) => baseQuery.filter(_.scopeValue === sv)
        case None     => baseQuery.filter(_.scopeValue.isEmpty)

      run(query.sortBy(_.lastAccessedAt.desc).result)
    }

  override def updateStatus(cacheId: UUID, status: CacheStatus): IO[Boolean] =
    withContext("updateStatus") {
      val q = table
        .filter(_.cacheId === cacheId)
        .map(e => (e.status, e.updatedAt))
        .update((status, Instant.now()))

      run(q).map(_ == 1)
    }

  override def updateContentHash(cacheId: UUID, contentHash: String): IO[Boolean] =
    withContext("updateContentHash") {
      val q = table
        .filter(_.cacheId === cacheId)
        .map(e => (e.contentHash, e.updatedAt))
        .update((Some(contentHash), Instant.now()))

      run(q).map(_ == 1)
    }

  override def recordAccess(cacheId: UUID): IO[Boolean] =
    withContext("recordAccess") {
      IO.executionContext.flatMap { implicit ec =>
        val q: DBIO[Int] = for
          currentOpt <- table.filter(_.cacheId === cacheId).map(_.accessCount).result.headOption
          updated <- currentOpt match
            case Some(currentCount) =>
              table
                .filter(_.cacheId === cacheId)
                .map(e => (e.accessCount, e.lastAccessedAt))
                .update((currentCount + 1, Instant.now()))
            case None => DBIO.successful(0)
        yield updated
        transactionally(q).map(_ == 1)
      }
    }

  override def findExpired(at: Instant, limit: Int): IO[Seq[CacheEntryRow]] =
    withContext("findExpired") {
      val query = table
        .filter(_.expiresAt.isDefined)
        .filter(_.expiresAt < at)
        .sortBy(_.expiresAt.asc.nullsLast)
        .take(limit)
        .result

      run(query)
    }

  override def findLeastRecentlyAccessed(limit: Int): IO[Seq[CacheEntryRow]] =
    withContext("findLeastRecentlyAccessed") {
      val query = table
        .filter(_.status === (CacheStatus.Ready: CacheStatus))
        .sortBy(_.lastAccessedAt.asc)
        .take(limit)
        .result

      run(query)
    }

  override def delete(cacheId: UUID): IO[Boolean] =
    withContext("delete") {
      val q = table
        .filter(_.cacheId === cacheId)
        .delete

      run(q).map(_ == 1)
    }
