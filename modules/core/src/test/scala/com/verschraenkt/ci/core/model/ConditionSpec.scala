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
package com.verschraenkt.ci.core.model

import cats.data.NonEmptyVector
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ConditionSpec extends AnyFunSuite with Matchers:

  test("Always condition should be the default") {
    Condition.Always shouldBe Condition.Always
  }

  test("Never condition should exist") {
    Condition.Never shouldBe Condition.Never
  }

  test("OnBranch with wildcard pattern") {
    val cond = Condition.OnBranch("release-*", PatternKind.Wildcard)
    cond shouldBe a[Condition.OnBranch]
  }

  test("OnBranch with regex pattern") {
    val cond = Condition.OnBranch("^release-\\d+\\.\\d+$", PatternKind.Regex)
    cond shouldBe a[Condition.OnBranch]
  }

  test("NotOnBranch condition") {
    val cond = Condition.NotOnBranch("main", PatternKind.Exact)
    cond shouldBe a[Condition.NotOnBranch]
  }

  test("OnEvent condition") {
    val cond = Condition.OnEvent("pull_request")
    cond match
      case Condition.OnEvent(event) => event shouldBe "pull_request"
      case _                        => fail("Expected OnEvent")
  }

  test("NotOnEvent condition") {
    val cond = Condition.NotOnEvent("push")
    cond match
      case Condition.NotOnEvent(event) => event shouldBe "push"
      case _                           => fail("Expected NotOnEvent")
  }

  test("OnTag with pattern") {
    val cond = Condition.OnTag("v*", PatternKind.Wildcard)
    cond shouldBe a[Condition.OnTag]
  }

  test("NotOnTag condition") {
    val cond = Condition.NotOnTag("beta", PatternKind.Exact)
    cond shouldBe a[Condition.NotOnTag]
  }

  test("OnPathsChanged with multiple paths") {
    val paths = NonEmptyVector.of("src/**", "tests/**")
    val cond  = Condition.OnPathsChanged(paths)
    cond match
      case Condition.OnPathsChanged(p) => p shouldBe paths
      case _                           => fail("Expected OnPathsChanged")
  }

  test("OnPathsNotChanged condition") {
    val paths = NonEmptyVector.one("docs/**")
    val cond  = Condition.OnPathsNotChanged(paths)
    cond match
      case Condition.OnPathsNotChanged(p) => p shouldBe paths
      case _                              => fail("Expected OnPathsNotChanged")
  }

  test("OnSuccess condition") {
    Condition.OnSuccess shouldBe Condition.OnSuccess
  }

  test("OnFailure condition") {
    Condition.OnFailure shouldBe Condition.OnFailure
  }

  test("EnvEquals condition") {
    val cond = Condition.EnvEquals("NODE_ENV", "production")
    cond match
      case Condition.EnvEquals(key, value) =>
        key shouldBe "NODE_ENV"
        value shouldBe "production"
      case _ => fail("Expected EnvEquals")
  }

  test("EnvNotEquals condition") {
    val cond = Condition.EnvNotEquals("DEBUG", "true")
    cond match
      case Condition.EnvNotEquals(key, value) =>
        key shouldBe "DEBUG"
        value shouldBe "true"
      case _ => fail("Expected EnvNotEquals")
  }

  test("EnvExists condition") {
    val cond = Condition.EnvExists("API_KEY")
    cond match
      case Condition.EnvExists(key) => key shouldBe "API_KEY"
      case _                        => fail("Expected EnvExists")
  }

  test("EnvNotExists condition") {
    val cond = Condition.EnvNotExists("DEPRECATED_VAR")
    cond match
      case Condition.EnvNotExists(key) => key shouldBe "DEPRECATED_VAR"
      case _                           => fail("Expected EnvNotExists")
  }

  test("OnSchedule condition") {
    val cond = Condition.OnSchedule("0 0 * * *")
    cond match
      case Condition.OnSchedule(cron) => cron shouldBe "0 0 * * *"
      case _                          => fail("Expected OnSchedule")
  }

  test("OnManualTrigger condition") {
    Condition.OnManualTrigger shouldBe Condition.OnManualTrigger
  }

  test("OnCommitMessage with pattern") {
    val cond = Condition.OnCommitMessage("fix:*", PatternKind.Wildcard)
    cond shouldBe a[Condition.OnCommitMessage]
  }

  test("OnAuthor condition") {
    val cond = Condition.OnAuthor("john.doe")
    cond match
      case Condition.OnAuthor(author) => author shouldBe "john.doe"
      case _                          => fail("Expected OnAuthor")
  }

  test("OnDraft condition") {
    Condition.OnDraft shouldBe Condition.OnDraft
  }

  test("NotOnDraft condition") {
    Condition.NotOnDraft shouldBe Condition.NotOnDraft
  }

  test("OnRepository condition") {
    val cond = Condition.OnRepository("owner/repo")
    cond match
      case Condition.OnRepository(repo) => repo shouldBe "owner/repo"
      case _                            => fail("Expected OnRepository")
  }

  test("NotOnAuthor condition") {
    val cond = Condition.NotOnAuthor("bot")
    cond match
      case Condition.NotOnAuthor(author) => author shouldBe "bot"
      case _                             => fail("Expected NotOnAuthor")
  }

  test("Expression condition") {
    val expr = "github.event_name == 'push' && github.ref == 'refs/heads/main'"
    val cond = Condition.Expression(expr)
    cond match
      case Condition.Expression(e) => e shouldBe expr
      case _                       => fail("Expected Expression")
  }

  test("And combinator with two conditions") {
    val cond1    = Condition.OnBranch("main", PatternKind.Exact)
    val cond2    = Condition.OnEvent("push")
    val combined = Condition.And(NonEmptyVector.of(cond1, cond2))
    combined match
      case Condition.And(conditions) => conditions.length shouldBe 2
      case _                         => fail("Expected And")
  }

  test("Or combinator with multiple conditions") {
    val cond1    = Condition.OnBranch("main", PatternKind.Exact)
    val cond2    = Condition.OnBranch("develop", PatternKind.Exact)
    val cond3    = Condition.OnTag("v*", PatternKind.Wildcard)
    val combined = Condition.Or(NonEmptyVector.of(cond1, cond2, cond3))
    combined match
      case Condition.Or(conditions) => conditions.length shouldBe 3
      case _                        => fail("Expected Or")
  }

  test("Not combinator") {
    val cond    = Condition.OnBranch("main", PatternKind.Exact)
    val negated = Condition.Not(cond)
    negated match
      case Condition.Not(c) => c shouldBe cond
      case _                => fail("Expected Not")
  }

  test("&& operator combines conditions into And") {
    val cond1    = Condition.OnBranch("main", PatternKind.Exact)
    val cond2    = Condition.OnEvent("push")
    val combined = cond1 && cond2
    combined shouldBe a[Condition.And]
  }

  test("&& operator flattens nested And conditions") {
    val cond1    = Condition.OnBranch("main", PatternKind.Exact)
    val cond2    = Condition.OnEvent("push")
    val cond3    = Condition.OnSuccess
    val combined = cond1 && cond2 && cond3
    combined match
      case Condition.And(conditions) => conditions.length shouldBe 3
      case _                         => fail("Expected And condition")
  }

  test("|| operator combines conditions into Or") {
    val cond1    = Condition.OnBranch("main", PatternKind.Exact)
    val cond2    = Condition.OnBranch("develop", PatternKind.Exact)
    val combined = cond1 || cond2
    combined shouldBe a[Condition.Or]
  }

  test("|| operator flattens nested Or conditions") {
    val cond1    = Condition.OnBranch("main", PatternKind.Exact)
    val cond2    = Condition.OnBranch("develop", PatternKind.Exact)
    val cond3    = Condition.OnBranch("staging", PatternKind.Exact)
    val combined = cond1 || cond2 || cond3
    combined match
      case Condition.Or(conditions) => conditions.length shouldBe 3
      case _                        => fail("Expected Or condition")
  }

  test("unary_! negates a condition") {
    val cond    = Condition.OnBranch("main", PatternKind.Exact)
    val negated = !cond
    negated shouldBe Condition.Not(cond)
  }

  test("Condition.all creates And from varargs") {
    val cond1    = Condition.OnBranch("main", PatternKind.Exact)
    val cond2    = Condition.OnEvent("push")
    val cond3    = Condition.OnSuccess
    val combined = Condition.all(cond1, cond2, cond3)
    combined match
      case Condition.And(conditions) => conditions.length shouldBe 3
      case _                         => fail("Expected And condition")
  }

  test("Condition.any creates Or from varargs") {
    val cond1    = Condition.OnBranch("main", PatternKind.Exact)
    val cond2    = Condition.OnBranch("develop", PatternKind.Exact)
    val combined = Condition.any(cond1, cond2)
    combined match
      case Condition.Or(conditions) => conditions.length shouldBe 2
      case _                        => fail("Expected Or condition")
  }

  test("Condition.onMainBranch helper") {
    val cond = Condition.onMainBranch
    cond shouldBe Condition.OnBranch("main", PatternKind.Exact)
  }

  test("Condition.onPR helper") {
    val cond = Condition.onPR
    cond shouldBe Condition.OnEvent("pull_request")
  }

  test("Condition.onPush helper") {
    val cond = Condition.onPush
    cond shouldBe Condition.OnEvent("push")
  }

  test("Condition.onReleaseBranch helper") {
    val cond = Condition.onReleaseBranch
    cond shouldBe Condition.OnBranch("release-*", PatternKind.Wildcard)
  }

  test("Condition.onVersionTag helper") {
    val cond = Condition.onVersionTag
    cond shouldBe Condition.OnTag("v*", PatternKind.Wildcard)
  }

  test("simplify: And(Always, X) => X") {
    val cond     = Condition.OnBranch("main", PatternKind.Exact)
    val combined = Condition.And(NonEmptyVector.of(Condition.Always, cond))
    combined.simplify shouldBe cond
  }

  test("simplify: And(Never, X) => Never") {
    val cond     = Condition.OnBranch("main", PatternKind.Exact)
    val combined = Condition.And(NonEmptyVector.of(Condition.Never, cond))
    combined.simplify shouldBe Condition.Never
  }

  test("simplify: Or(Never, X) => X") {
    val cond     = Condition.OnBranch("main", PatternKind.Exact)
    val combined = Condition.Or(NonEmptyVector.of(Condition.Never, cond))
    combined.simplify shouldBe cond
  }

  test("simplify: Or(Always, X) => Always") {
    val cond     = Condition.OnBranch("main", PatternKind.Exact)
    val combined = Condition.Or(NonEmptyVector.of(Condition.Always, cond))
    combined.simplify shouldBe Condition.Always
  }

  test("simplify: Not(Always) => Never") {
    val negated = Condition.Not(Condition.Always)
    negated.simplify shouldBe Condition.Never
  }

  test("simplify: Not(Never) => Always") {
    val negated = Condition.Not(Condition.Never)
    negated.simplify shouldBe Condition.Always
  }

  test("simplify: Not(Not(X)) => X") {
    val cond          = Condition.OnBranch("main", PatternKind.Exact)
    val doubleNegated = Condition.Not(Condition.Not(cond))
    doubleNegated.simplify shouldBe cond
  }

  test("simplify: And with single condition => that condition") {
    val cond     = Condition.OnBranch("main", PatternKind.Exact)
    val combined = Condition.And(NonEmptyVector.one(cond))
    combined.simplify shouldBe cond
  }

  test("simplify: Or with single condition => that condition") {
    val cond     = Condition.OnBranch("main", PatternKind.Exact)
    val combined = Condition.Or(NonEmptyVector.one(cond))
    combined.simplify shouldBe cond
  }

  test("simplify: nested simplification") {
    val cond = Condition.OnBranch("main", PatternKind.Exact)
    val nested = Condition.And(
      NonEmptyVector.of(
        Condition.Always,
        Condition.Or(NonEmptyVector.of(Condition.Never, cond))
      )
    )
    nested.simplify shouldBe cond
  }

  test("Complex condition combination") {
    val cond = (Condition.onMainBranch || Condition.onReleaseBranch) &&
      Condition.onPush &&
      !Condition.OnDraft
    cond shouldBe a[Condition.And]
  }

  test("De Morgan's law verification structure") {
    val cond1 = Condition.OnBranch("main", PatternKind.Exact)
    val cond2 = Condition.OnEvent("push")

    // !(A && B) should equal !A || !B
    val left  = !(cond1 && cond2)
    val right = !cond1 || !cond2

    // We verify structure, not logical equivalence
    left shouldBe a[Condition.Not]
    right shouldBe a[Condition.Or]
  }

  // Tests for Issue 2.3: De Morgan simplification doesn't infinite loop
  test("simplify: Not(And) applies De Morgan's law correctly") {
    val cond1  = Condition.OnBranch("main", PatternKind.Exact)
    val cond2  = Condition.OnEvent("push")
    val notAnd = Condition.Not(Condition.And(NonEmptyVector.of(cond1, cond2)))

    val simplified = notAnd.simplify
    // Should become Or(Not(cond1), Not(cond2))
    simplified shouldBe a[Condition.Or]
    simplified match
      case Condition.Or(cs) =>
        cs.length shouldBe 2
        cs.toVector.foreach { c => c shouldBe a[Condition.Not] }
      case _ => fail("Expected Or condition")
  }

  test("simplify: Not(Or) applies De Morgan's law correctly") {
    val cond1 = Condition.OnBranch("main", PatternKind.Exact)
    val cond2 = Condition.OnEvent("push")
    val notOr = Condition.Not(Condition.Or(NonEmptyVector.of(cond1, cond2)))

    val simplified = notOr.simplify
    // Should become And(Not(cond1), Not(cond2))
    simplified shouldBe a[Condition.And]
    simplified match
      case Condition.And(cs) =>
        cs.length shouldBe 2
        cs.toVector.foreach { c => c shouldBe a[Condition.Not] }
      case _ => fail("Expected And condition")
  }

  test("simplify: deeply nested De Morgan does not stack overflow") {
    // Create a deeply nested structure: Not(And(Not(Or(Not(And(...))))))
    var cond: Condition = Condition.OnBranch("main", PatternKind.Exact)
    for _ <- 1 to 20 do
      cond = Condition.Not(Condition.And(NonEmptyVector.of(cond, Condition.OnEvent("push"))))

    // This should complete without stack overflow
    val simplified = cond.simplify
    // Just verify it returns something (the actual structure is complex)
    simplified should not be null
  }

  test("simplify: Not(Not(And)) simplifies correctly via De Morgan") {
    val cond1        = Condition.OnBranch("main", PatternKind.Exact)
    val cond2        = Condition.OnEvent("push")
    val doubleNegAnd = Condition.Not(Condition.Not(Condition.And(NonEmptyVector.of(cond1, cond2))))

    val simplified = doubleNegAnd.simplify
    // Double negation should cancel out, leaving the And
    simplified shouldBe a[Condition.And]
  }
