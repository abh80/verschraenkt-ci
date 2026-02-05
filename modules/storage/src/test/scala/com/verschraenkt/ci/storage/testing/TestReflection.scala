package com.verschraenkt.ci.storage.testing

object TestReflection:
  def invokeNoArg[A](target: AnyRef, methodName: String): A =
    val method = target.getClass.getDeclaredMethod(methodName)
    method.setAccessible(true)
    method.invoke(target).asInstanceOf[A]

  def invokeArgs[A](target: AnyRef, methodName: String, args: AnyRef*): A =
    val method = target.getClass.getDeclaredMethods
      .find(m => m.getName == methodName && m.getParameterCount == args.length)
      .getOrElse {
        throw new NoSuchMethodException(s"${target.getClass.getName}#$methodName(${args.length} args)")
      }
    method.setAccessible(true)
    method.invoke(target, args*).asInstanceOf[A]

  def invokeCurried[A](
      target: AnyRef,
      methodName: String,
      paramLists: List[List[Any]]
  ): A =
    val cls = target.getClass

    val methods = cls.getMethods.filter(_.getName == methodName)

    val method =
      methods
        .find { m =>
          val totalParams =
            if m.getParameterCount == 0 then 0
            else 1 // first list
          totalParams == paramLists.length
        }
        .getOrElse {
          throw new NoSuchMethodException(
            s"${cls.getName}#$methodName with ${paramLists.length} parameter lists"
          )
        }

    var result: Any = target

    paramLists.zipWithIndex.foreach { (params, idx) =>
      val m =
        if idx == 0 then method
        else
          result.getClass.getMethods
            .find(_.getName == "apply")
            .getOrElse(
              throw new NoSuchMethodException("Missing apply on curried result")
            )

      result = m.invoke(result, params.map(_.asInstanceOf[AnyRef])*)

    }

    result.asInstanceOf[A]
