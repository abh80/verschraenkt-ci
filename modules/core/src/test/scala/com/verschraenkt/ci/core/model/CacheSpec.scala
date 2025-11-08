package com.verschraenkt.ci.core.model

import cats.data.NonEmptyList
import munit.FunSuite

class CacheSpec extends FunSuite:

  test("CacheScope should have correct integer values") {
    assertEquals(CacheScope.Global.intVal, 0)
    assertEquals(CacheScope.Branch.intVal, 1)
    assertEquals(CacheScope.PullRequest.intVal, 2)
    assertEquals(CacheScope.Tag.intVal, 3)
  }

  test("CacheScope.fromInt should convert valid integers") {
    assertEquals(CacheScope.fromInt(0), Some(CacheScope.Global))
    assertEquals(CacheScope.fromInt(1), Some(CacheScope.Branch))
    assertEquals(CacheScope.fromInt(2), Some(CacheScope.PullRequest))
    assertEquals(CacheScope.fromInt(3), Some(CacheScope.Tag))
  }

  test("CacheScope.fromInt should return None for invalid integers") {
    assertEquals(CacheScope.fromInt(-1), None)
    assertEquals(CacheScope.fromInt(4), None)
    assertEquals(CacheScope.fromInt(100), None)
  }

  test("CacheKey.literal should create a normalized key") {
    val key = CacheKey.literal("my-cache-key")
    assertEquals(key.value, "my-cache-key")
  }

  test("CacheKey.literal should normalize special characters") {
    val key = CacheKey.literal("my@cache#key!")
    assertEquals(key.value, "my_cache_key_")
  }

  test("CacheKey.literal should preserve allowed characters") {
    val key = CacheKey.literal("cache.key_with-allowed.chars")
    assertEquals(key.value, "cache.key_with-allowed.chars")
  }

  test("CacheKey.literal should collapse multiple underscores") {
    val key = CacheKey.literal("cache___key___here")
    assertEquals(key.value, "cache_key_here")
  }

  test("CacheKey.literal should trim whitespace") {
    val key = CacheKey.literal("  cache-key  ")
    assertEquals(key.value, "cache-key")
  }

  test("CacheKey.literal should truncate long keys with hash") {
    val longKey = "a" * 200
    val key = CacheKey.literal(longKey)
    assert(key.value.length <= 128)
    assert(key.value.startsWith("a" * 32 + "-"))
  }

  test("CacheKey.fromParts should join parts with pipe separator") {
    val key = CacheKey.fromParts("part1", "part2", "part3")
    assertEquals(key.value, "part1|part2|part3")
  }

  test("CacheKey.fromParts should work with single part") {
    val key = CacheKey.fromParts("single")
    assertEquals(key.value, "single")
  }

  test("CacheKey.fromParts should normalize the result") {
    val key = CacheKey.fromParts("part@1", "part#2")
    assertEquals(key.value, "part_1|part_2")
  }

  test("CacheKey.namespace should create namespaced key") {
    val key = CacheKey.literal("my-key")
    val namespaced = CacheKey.namespace("main", key)
    assertEquals(namespaced.value, "main:my-key")
  }

  test("CacheKey.namespace should normalize the result") {
    val key = CacheKey.literal("my-key")
    val namespaced = CacheKey.namespace("feature/branch", key)
    assertEquals(namespaced.value, "feature_branch:my-key")
  }

  test("CacheKey.template should substitute variables") {
    val template = "cache-${branch}-${version}"
    val ctx = Map("branch" -> "main", "version" -> "1.0")
    val result = CacheKey.template(template, ctx)
    assert(result.isRight)
    assertEquals(result.toOption.get.value, "cache-main-1.0")
  }

  test("CacheKey.template should return error for missing variables") {
    val template = "cache-${branch}-${version}"
    val ctx = Map("branch" -> "main")
    val result = CacheKey.template(template, ctx)
    assert(result.isLeft)
    assertEquals(result.left.toOption.get, "version")
  }

  test("CacheKey.template should return multiple missing variables") {
    val template = "cache-${branch}-${version}-${os}"
    val ctx = Map("branch" -> "main")
    val result = CacheKey.template(template, ctx)
    assert(result.isLeft)
    val missing = result.left.toOption.get.split(",").toSet
    assertEquals(missing, Set("version", "os"))
  }

  test("CacheKey.template should work with no variables") {
    val template = "static-cache-key"
    val ctx = Map.empty[String, String]
    val result = CacheKey.template(template, ctx)
    assert(result.isRight)
    assertEquals(result.toOption.get.value, "static-cache-key")
  }

  test("CacheKey.scoped should not modify Global scope") {
    val key = CacheKey.literal("my-key")
    val scoped = CacheKey.scoped(CacheScope.Global, key, Map("branch" -> "main"))
    assertEquals(scoped, key)
  }

  test("CacheKey.scoped should namespace Branch scope") {
    val key = CacheKey.literal("my-key")
    val scoped = CacheKey.scoped(CacheScope.Branch, key, Map("branch" -> "main"))
    assertEquals(scoped.value, "main:my-key")
  }

  test("CacheKey.scoped should use 'unknown' for missing Branch context") {
    val key = CacheKey.literal("my-key")
    val scoped = CacheKey.scoped(CacheScope.Branch, key, Map.empty)
    assertEquals(scoped.value, "unknown:my-key")
  }

  test("CacheKey.scoped should namespace PullRequest scope") {
    val key = CacheKey.literal("my-key")
    val scoped = CacheKey.scoped(CacheScope.PullRequest, key, Map("pr" -> "123"))
    assertEquals(scoped.value, "123:my-key")
  }

  test("CacheKey.scoped should namespace Tag scope") {
    val key = CacheKey.literal("my-key")
    val scoped = CacheKey.scoped(CacheScope.Tag, key, Map("tag" -> "v1.0.0"))
    assertEquals(scoped.value, "v1.0.0:my-key")
  }

  test("Cache.forBranch should create branch-scoped key") {
    val key = CacheKey.literal("my-key")
    val scoped = Cache.forBranch(key, "develop")
    assertEquals(scoped.value, "develop:my-key")
  }

  test("Cache.forPR should create PR-scoped key") {
    val key = CacheKey.literal("my-key")
    val scoped = Cache.forPR(key, "456")
    assertEquals(scoped.value, "456:my-key")
  }

  test("Cache.forTag should create tag-scoped key") {
    val key = CacheKey.literal("my-key")
    val scoped = Cache.forTag(key, "v2.0.0")
    assertEquals(scoped.value, "v2.0.0:my-key")
  }

  test("RestoreCache should implement CacheLike") {
    val key = CacheKey.literal("test-key")
    val paths = NonEmptyList.of("/path1", "/path2")
    val restore = Cache.RestoreCache(key, paths, CacheScope.Global)

    assertEquals(restore.key, key)
    assertEquals(restore.scope, CacheScope.Global)
    assertEquals(restore.paths.toList, List("/path1", "/path2"))
  }

  test("SaveCache should implement CacheLike") {
    val key = CacheKey.literal("test-key")
    val paths = NonEmptyList.of("/path1", "/path2")
    val save = Cache.SaveCache(key, paths, CacheScope.Branch)

    assertEquals(save.key, key)
    assertEquals(save.scope, CacheScope.Branch)
    assertEquals(save.paths.toList, List("/path1", "/path2"))
  }

  test("Cache.restoreForBranch should create scoped restore operation") {
    val key = CacheKey.literal("deps")
    val paths = NonEmptyList.of("node_modules")
    val restore = Cache.restoreForBranch(key, paths, "main")

    assertEquals(restore.key.value, "main:deps")
    assertEquals(restore.scope, CacheScope.Branch)
    assertEquals(restore.paths.toList, List("node_modules"))
  }

  test("Cache.saveForBranch should create scoped save operation") {
    val key = CacheKey.literal("deps")
    val paths = NonEmptyList.of("node_modules")
    val save = Cache.saveForBranch(key, paths, "main")

    assertEquals(save.key.value, "main:deps")
    assertEquals(save.scope, CacheScope.Branch)
    assertEquals(save.paths.toList, List("node_modules"))
  }

  test("Cache.restoreForPR should create scoped restore operation") {
    val key = CacheKey.literal("build")
    val paths = NonEmptyList.of("target")
    val restore = Cache.restoreForPR(key, paths, "789")

    assertEquals(restore.key.value, "789:build")
    assertEquals(restore.scope, CacheScope.PullRequest)
    assertEquals(restore.paths.toList, List("target"))
  }

  test("Cache.saveForPR should create scoped save operation") {
    val key = CacheKey.literal("build")
    val paths = NonEmptyList.of("target")
    val save = Cache.saveForPR(key, paths, "789")

    assertEquals(save.key.value, "789:build")
    assertEquals(save.scope, CacheScope.PullRequest)
    assertEquals(save.paths.toList, List("target"))
  }

  test("Cache.restoreForTag should create scoped restore operation") {
    val key = CacheKey.literal("artifacts")
    val paths = NonEmptyList.of("dist")
    val restore = Cache.restoreForTag(key, paths, "v1.2.3")

    assertEquals(restore.key.value, "v1.2.3:artifacts")
    assertEquals(restore.scope, CacheScope.Tag)
    assertEquals(restore.paths.toList, List("dist"))
  }

  test("Cache.saveForTag should create scoped save operation") {
    val key = CacheKey.literal("artifacts")
    val paths = NonEmptyList.of("dist")
    val save = Cache.saveForTag(key, paths, "v1.2.3")

    assertEquals(save.key.value, "v1.2.3:artifacts")
    assertEquals(save.scope, CacheScope.Tag)
    assertEquals(save.paths.toList, List("dist"))
  }

  test("Cache.restore should create restore with custom scope") {
    val key = CacheKey.literal("test-cache")
    val paths = NonEmptyList.of("/cache/path")
    val ctx = Map("branch" -> "feature")
    val restore = Cache.restore(CacheScope.Branch, key, paths, ctx)

    assertEquals(restore.key.value, "feature:test-cache")
    assertEquals(restore.scope, CacheScope.Branch)
  }

  test("Cache.save should create save with custom scope") {
    val key = CacheKey.literal("test-cache")
    val paths = NonEmptyList.of("/cache/path")
    val ctx = Map("pr" -> "999")
    val save = Cache.save(CacheScope.PullRequest, key, paths, ctx)

    assertEquals(save.key.value, "999:test-cache")
    assertEquals(save.scope, CacheScope.PullRequest)
  }

  test("Cache.restore should work with Global scope and empty context") {
    val key = CacheKey.literal("global-cache")
    val paths = NonEmptyList.of("/data")
    val restore = Cache.restore(CacheScope.Global, key, paths)

    assertEquals(restore.key.value, "global-cache")
    assertEquals(restore.scope, CacheScope.Global)
  }

  test("CacheKey normalization should handle pipes and colons") {
    val key = CacheKey.literal("part1|part2:part3")
    assertEquals(key.value, "part1|part2:part3")
  }

  test("CacheKey normalization should handle mixed characters") {
    val key = CacheKey.literal("Cache_Key-123.Test")
    assertEquals(key.value, "Cache_Key-123.Test")
  }

  test("CacheKey should be consistent for same input") {
    val key1 = CacheKey.literal("consistent-key")
    val key2 = CacheKey.literal("consistent-key")
    assertEquals(key1, key2)
  }

  test("CacheKey hash should be consistent for long keys") {
    val longKey = "x" * 200
    val key1 = CacheKey.literal(longKey)
    val key2 = CacheKey.literal(longKey)
    assertEquals(key1, key2)
  }

  test("CacheKey should handle empty strings") {
    val key = CacheKey.literal("")
    assertEquals(key.value, "")
  }

  test("RestoreCache should have default Global scope") {
    val key = CacheKey.literal("test")
    val paths = NonEmptyList.of("/path")
    val restore = Cache.RestoreCache(key, paths)
    assertEquals(restore.scope, CacheScope.Global)
  }

  test("SaveCache should have default Global scope") {
    val key = CacheKey.literal("test")
    val paths = NonEmptyList.of("/path")
    val save = Cache.SaveCache(key, paths)
    assertEquals(save.scope, CacheScope.Global)
  }

  test("CacheKey.template should handle variable names with underscores") {
    val template = "cache-${my_var}-${another_var_123}"
    val ctx = Map("my_var" -> "value1", "another_var_123" -> "value2")
    val result = CacheKey.template(template, ctx)
    assert(result.isRight)
    assertEquals(result.toOption.get.value, "cache-value1-value2")
  }

  test("CacheKey.fromParts should handle many parts") {
    val key = CacheKey.fromParts("os", "linux", "arch", "x64", "node", "18")
    assertEquals(key.value, "os|linux|arch|x64|node|18")
  }

  test("Multiple operations should compose correctly") {
    val baseKey = CacheKey.fromParts("node", "modules")
    val namespaced = CacheKey.namespace("main", baseKey)
    val paths = NonEmptyList.of("node_modules", "package-lock.json")
    val save = Cache.SaveCache(namespaced, paths, CacheScope.Branch)

    assertEquals(save.key.value, "main:node|modules")
    assertEquals(save.scope, CacheScope.Branch)
    assertEquals(save.paths.length, 2)
  }