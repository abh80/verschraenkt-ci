package com.verschraenkt.ci.core.model

import cats.data.NonEmptyList
import scala.util.matching.Regex
import java.security.MessageDigest
import java.nio.charset.StandardCharsets.UTF_8

/** Defines different scoping levels for caches */
enum CacheScope(val intVal: Int):
  /** Global cache scope, available to all workflows */
  case Global extends CacheScope(0)

  /** Branch-specific cache scope */
  case Branch extends CacheScope(1)

  /** Pull request-specific cache scope */
  case PullRequest extends CacheScope(2)

  /** Tag-specific cache scope */
  case Tag extends CacheScope(3)

/** Represents a normalized cache key */
final case class CacheKey private (value: String) extends AnyVal

/** Common trait for cache operations */
sealed trait CacheLike:
  /** The scope this cache operation applies to */
  def scope: CacheScope

  /** The key used to identify this cache */
  def key: CacheKey

/** Factory methods for creating and manipulating cache keys */
object CacheKey:
  private val MaxLen = 128
  private val Var    = "\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}".r

  /** Creates a cache key from a literal string */
  def literal(s: String): CacheKey =
    normalize(s)

  /** Creates a cache key by joining multiple parts with | separator */
  def fromParts(head: String, tail: String*): CacheKey =
    normalize((head +: tail.toVector).mkString("|"))

  /** Creates a namespaced cache key */
  def namespace(ns: String, key: CacheKey): CacheKey =
    normalize(s"$ns:${key.value}")

  /** Creates a cache key from a template string with variable substitution */
  def template(tmpl: String, ctx: Map[String, String]): Either[String, CacheKey] =
    val missing = Var.findAllMatchIn(tmpl).map(_.group(1)).filterNot(ctx.contains).toSet
    if missing.nonEmpty then Left(missing.mkString(","))
    else
      val filled = Var.replaceAllIn(tmpl, m => Regex.quoteReplacement(ctx(m.group(1))))
      Right(normalize(filled))

  /** Creates a scoped cache key */
  def scoped(scope: CacheScope, key: CacheKey, ctx: Map[String, String] = Map.empty): CacheKey =
    scope match
      case CacheScope.Global      => key
      case CacheScope.Branch      => namespace(ctx.getOrElse("branch", "unknown"), key)
      case CacheScope.PullRequest => namespace(ctx.getOrElse("pr", "unknown"), key)
      case CacheScope.Tag         => namespace(ctx.getOrElse("tag", "unknown"), key)

  private def normalize(s: String): CacheKey =
    val trimmed = s.trim
    val mapped = trimmed.map { ch =>
      if ch.isLetterOrDigit || ch == '.' || ch == '_' || ch == '-' || ch == '|' || ch == ':' then ch else '_'
    }.mkString
    val compact = mapped.replaceAll("_+", "_")
    val v =
      if compact.length <= MaxLen then compact
      else s"${compact.take(32)}-${sha256Hex(compact).take(48)}"
    CacheKey(v)

  private def sha256Hex(s: String): String =
    val md = MessageDigest.getInstance("SHA-256")
    md.digest(s.getBytes(UTF_8)).map("%02x".format(_)).mkString

/** Companion object for CacheScope enum */
object CacheScope:
  /** Converts an integer to a CacheScope */
  def fromInt(i: Int): Option[CacheScope] =
    i match
      case 0 => Some(CacheScope.Global)
      case 1 => Some(CacheScope.Branch)
      case 2 => Some(CacheScope.PullRequest)
      case 3 => Some(CacheScope.Tag)
      case _ => None

/** Factory methods for creating cache operations */
object Cache:
  /** Creates a branch-scoped cache key */
  def forBranch(key: CacheKey, branch: String): CacheKey =
    CacheKey.namespace(branch, key)

  /** Creates a PR-scoped cache key */
  def forPR(key: CacheKey, pr: String): CacheKey =
    CacheKey.namespace(pr, key)

  /** Creates a tag-scoped cache key */
  def forTag(key: CacheKey, tag: String): CacheKey =
    CacheKey.namespace(tag, key)

  /** Creates a restore cache operation for a branch */
  def restoreForBranch(key: CacheKey, paths: NonEmptyList[String], branch: String): RestoreCache =
    val ctx       = Map("branch" -> branch)
    val scopedKey = CacheKey.scoped(CacheScope.Branch, key, ctx)
    RestoreCache(scopedKey, paths, CacheScope.Branch)

  /** Creates a save cache operation for a branch */
  def saveForBranch(key: CacheKey, paths: NonEmptyList[String], branch: String): SaveCache =
    val ctx       = Map("branch" -> branch)
    val scopedKey = CacheKey.scoped(CacheScope.Branch, key, ctx)
    SaveCache(scopedKey, paths, CacheScope.Branch)

  /** Creates a restore cache operation for a PR */
  def restoreForPR(key: CacheKey, paths: NonEmptyList[String], pr: String): RestoreCache =
    val ctx       = Map("pr" -> pr)
    val scopedKey = CacheKey.scoped(CacheScope.PullRequest, key, ctx)
    RestoreCache(scopedKey, paths, CacheScope.PullRequest)

  /** Creates a save cache operation for a PR */
  def saveForPR(key: CacheKey, paths: NonEmptyList[String], pr: String): SaveCache =
    val ctx       = Map("pr" -> pr)
    val scopedKey = CacheKey.scoped(CacheScope.PullRequest, key, ctx)
    SaveCache(scopedKey, paths, CacheScope.PullRequest)

  /** Creates a restore cache operation for a tag */
  def restoreForTag(key: CacheKey, paths: NonEmptyList[String], tag: String): RestoreCache =
    val ctx       = Map("tag" -> tag)
    val scopedKey = CacheKey.scoped(CacheScope.Tag, key, ctx)
    RestoreCache(scopedKey, paths, CacheScope.Tag)

  /** Creates a save cache operation for a tag */
  def saveForTag(key: CacheKey, paths: NonEmptyList[String], tag: String): SaveCache =
    val ctx       = Map("tag" -> tag)
    val scopedKey = CacheKey.scoped(CacheScope.Tag, key, ctx)
    SaveCache(scopedKey, paths, CacheScope.Tag)

  /** Creates a restore cache operation with custom scope */
  def restore(
      scope: CacheScope,
      key: CacheKey,
      paths: NonEmptyList[String],
      ctx: Map[String, String] = Map.empty
  ): RestoreCache =
    val scopedKey = CacheKey.scoped(scope, key, ctx)
    RestoreCache(scopedKey, paths, scope)

  /** Creates a save cache operation with custom scope */
  def save(
      scope: CacheScope,
      key: CacheKey,
      paths: NonEmptyList[String],
      ctx: Map[String, String] = Map.empty
  ): SaveCache =
    val scopedKey = CacheKey.scoped(scope, key, ctx)
    SaveCache(scopedKey, paths, scope)

  /** Represents a restore cache operation */
  final case class RestoreCache(
      key: CacheKey,
      paths: NonEmptyList[String],
      scope: CacheScope = CacheScope.Global
  ) extends CacheLike

  /** Represents a save cache operation */
  final case class SaveCache(
      key: CacheKey,
      paths: NonEmptyList[String],
      scope: CacheScope = CacheScope.Global
  ) extends CacheLike
