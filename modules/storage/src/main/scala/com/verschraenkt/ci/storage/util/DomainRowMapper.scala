package com.verschraenkt.ci.storage.util

import io.circe.{ Decoder, Encoder }

trait DomainRowMapper[Domain, Row, Metadata]:
  /** Convert domain model to database row */
  def fromDomain(domain: Domain, metadata: Metadata)(using Encoder[Domain]): Row

  /** Convert database row to domain model */
  def toDomain(row: Row)(using Decoder[Domain]): Either[io.circe.Error, Domain]
