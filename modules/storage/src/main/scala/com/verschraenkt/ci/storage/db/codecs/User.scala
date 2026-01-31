package com.verschraenkt.ci.storage.db.codecs

// TODO: Remove before flight: temporary assumption of user as string
opaque type User = String
object User:
  def apply(s: String): User = s
  extension (u: User) def unwrap: String = u