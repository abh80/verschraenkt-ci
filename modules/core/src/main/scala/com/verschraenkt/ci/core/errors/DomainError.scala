package com.verschraenkt.ci.core.errors

import scala.util.control.NoStackTrace

abstract class DomainError(val message: String)
  extends Exception(message)
    with NoStackTrace