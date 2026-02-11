/*
 * Copyright (c) 2025 abh80 on gitlab.com. All rights reserved.
 *
 * This file and all its contents are originally written, developed, and solely owned by abh80 on gitlab.com.
 * Every part of the code has been manually authored by abh80 on gitlab.com without the use of any artificial intelligence
 * tools for code generation. AI assistance was limited exclusively to generating or suggesting comments, if any.
 *
 * Unauthorized use, distribution, or reproduction is prohibited without explicit permission from the owner.
 * See License
 */
package com.verschraenkt.ci.storage.errors

import com.verschraenkt.ci.core.errors.DomainError
import com.verschraenkt.ci.core.utils.Location
sealed abstract class StorageError(
    override val message: String,
    override val source: Option[String] = None,
    override val location: Option[Location] = None,
    override val causeOpt: Option[Throwable] = None
) extends DomainError(message, source, location, causeOpt):
  def withSource(s: String): StorageError
  def withLocation(l: Location): StorageError
  def withCause(t: Throwable): StorageError
  def withMessage(m: String): StorageError
object StorageError:
  case class NotFound(
      entity: String,
      id: String,
      override val source: Option[String] = None,
      override val location: Option[Location] = None,
      override val causeOpt: Option[Throwable] = None
  ) extends StorageError(s"$entity with id '$id' not found", source, location, causeOpt):
    def withSource(s: String): NotFound     = copy(source = Some(s))
    def withLocation(l: Location): NotFound = copy(location = Some(l))
    def withCause(t: Throwable): NotFound   = copy(causeOpt = Some(t))
    def withMessage(m: String): NotFound    = this // message is derived from entity/id
  case class DuplicateKey(
      entity: String,
      id: String,
      override val source: Option[String] = None,
      override val location: Option[Location] = None,
      override val causeOpt: Option[Throwable] = None
  ) extends StorageError(s"$entity with id '$id' already exists", source, location, causeOpt):
    def withSource(s: String): DuplicateKey     = copy(source = Some(s))
    def withLocation(l: Location): DuplicateKey = copy(location = Some(l))
    def withCause(t: Throwable): DuplicateKey   = copy(causeOpt = Some(t))
    def withMessage(m: String): DuplicateKey    = this
  case class ConnectionFailed(
      cause: Throwable,
      override val source: Option[String] = None,
      override val location: Option[Location] = None
  ) extends StorageError(s"Database connection failed: ${cause.getMessage}", source, location, Some(cause)):
    def withSource(s: String): ConnectionFailed     = copy(source = Some(s))
    def withLocation(l: Location): ConnectionFailed = copy(location = Some(l))
    def withCause(t: Throwable): ConnectionFailed   = copy(cause = t)
    def withMessage(m: String): ConnectionFailed    = this
  case class TransactionFailed(
      cause: Throwable,
      override val source: Option[String] = None,
      override val location: Option[Location] = None
  ) extends StorageError(s"Transaction failed: ${cause.getMessage}", source, location, Some(cause)):
    def withSource(s: String): TransactionFailed     = copy(source = Some(s))
    def withLocation(l: Location): TransactionFailed = copy(location = Some(l))
    def withCause(t: Throwable): TransactionFailed   = copy(cause = t)
    def withMessage(m: String): TransactionFailed    = this
  case class DecodingFailed(
      msg: String,
      override val source: Option[String] = None,
      override val location: Option[Location] = None,
      override val causeOpt: Option[Throwable] = None
  ) extends StorageError(s"JSON decoding failed: $msg", source, location, causeOpt):
    def withSource(s: String): DecodingFailed     = copy(source = Some(s))
    def withLocation(l: Location): DecodingFailed = copy(location = Some(l))
    def withCause(t: Throwable): DecodingFailed   = copy(causeOpt = Some(t))
    def withMessage(m: String): DecodingFailed    = copy(msg = m)
  case class ObjectStoreError(
      operation: String,
      key: String,
      cause: Throwable,
      override val source: Option[String] = None,
      override val location: Option[Location] = None
  ) extends StorageError(
        s"Object store $operation failed for key '$key': ${cause.getMessage}",
        source,
        location,
        Some(cause)
      ):
    def withSource(s: String): ObjectStoreError     = copy(source = Some(s))
    def withLocation(l: Location): ObjectStoreError = copy(location = Some(l))
    def withCause(t: Throwable): ObjectStoreError   = copy(cause = t)
    def withMessage(m: String): ObjectStoreError    = this
  case class DeserializationFailed(
      cause: Throwable,
      override val source: Option[String] = None,
      override val location: Option[Location] = None
  ) extends StorageError(
        s"Deserialization for database object failed: ${cause.getMessage}",
        source,
        location,
        Some(cause)
      ):
    def withSource(s: String): DeserializationFailed     = copy(source = Some(s))
    def withLocation(l: Location): DeserializationFailed = copy(location = Some(l))
    def withCause(t: Throwable): DeserializationFailed   = copy(cause = t)
    def withMessage(m: String): DeserializationFailed    = this
