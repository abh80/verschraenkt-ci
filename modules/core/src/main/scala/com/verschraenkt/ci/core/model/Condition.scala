package com.verschraenkt.ci.core.model

import cats.data.NonEmptyVector

/** Pattern matching strategy for branch/tag conditions */
enum PatternKind:
  case Wildcard // Simple glob patterns: *, ?, [abc]
  case Regex    // Full regex support
  case Exact    // Exactly match it like as it is

/** Represents conditions for workflow and job execution */
enum Condition derives CanEqual:
  // Always/Never
  case Always
  case Never

  // Branch conditions
  case OnBranch(pattern: String, kind: PatternKind = PatternKind.Wildcard)
  case NotOnBranch(pattern: String, kind: PatternKind = PatternKind.Wildcard)

  // Event/trigger conditions
  case OnEvent(event: String)
  case NotOnEvent(event: String)

  // Tag conditions
  case OnTag(pattern: String, kind: PatternKind = PatternKind.Wildcard)
  case NotOnTag(pattern: String, kind: PatternKind = PatternKind.Wildcard)

  // File path conditions
  case OnPathsChanged(paths: NonEmptyVector[String])
  case OnPathsNotChanged(paths: NonEmptyVector[String])

  // Status conditions
  case OnSuccess
  case OnFailure
  case OnCancelled

  // Environment variable conditions
  case EnvEquals(key: String, value: String)
  case EnvNotEquals(key: String, value: String)
  case EnvExists(key: String)
  case EnvNotExists(key: String)

  // Commit/PR conditions
  case OnCommitMessage(pattern: String, kind: PatternKind = PatternKind.Wildcard)
  case OnAuthor(author: String)
  case NotOnAuthor(author: String)
  case OnPullRequest
  case OnDraft
  case NotOnDraft

  // Schedule/timing conditions
  case OnSchedule(cron: String)
  case OnManualTrigger
  case OnWorkflowDispatch
  case OnWorkflowCall

  // Repository conditions
  case OnRepository(repo: String)
  case IsFork
  case IsNotFork
  case IsPrivate
  case IsPublic

  // Label conditions
  case HasLabel(label: String)
  case HasAllLabels(labels: NonEmptyVector[String])
  case HasAnyLabel(labels: NonEmptyVector[String])
  case NotHasLabel(label: String)

  // Actor/permissions conditions
  case ActorIs(username: String)
  case ActorIsNot(username: String)
  case ActorInTeam(team: String)
  case HasPermission(permission: String)

  // Logical combinators
  case And(conditions: NonEmptyVector[Condition])
  case Or(conditions: NonEmptyVector[Condition])
  case Not(condition: Condition)

  // Custom expression
  case Expression(expr: String)

  /** Simplifies this condition by applying logical rules */
  def simplify: Condition = Condition.simplify(this)

/** Companion object with factory methods and utilities */
object Condition:
  // Common event types
  val onPush: Condition            = OnEvent("push")
  val onPullRequest: Condition     = OnEvent("pull_request")
  val onRelease: Condition         = OnEvent("release")
  val onIssue: Condition           = OnEvent("issue")
  val onIssueComment: Condition    = OnEvent("issue_comment")
  val onWorkflowRun: Condition     = OnEvent("workflow_run")
  val onCheckRun: Condition        = OnEvent("check_run")
  val onCheckSuite: Condition      = OnEvent("check_suite")
  val onCreate: Condition          = OnEvent("create")
  val onDelete: Condition          = OnEvent("delete")
  val onDeployment: Condition      = OnEvent("deployment")
  val onFork: Condition            = OnEvent("fork")
  val onGollum: Condition          = OnEvent("gollum")
  val onPageBuild: Condition       = OnEvent("page_build")
  val onPublic: Condition          = OnEvent("public")
  val onRegistryPackage: Condition = OnEvent("registry_package")
  val onStatus: Condition          = OnEvent("status")
  val onWatch: Condition           = OnEvent("watch")

  // Common branch patterns
  def onMainBranch: Condition    = OnBranch("main", PatternKind.Exact)
  def onMasterBranch: Condition  = OnBranch("master", PatternKind.Exact)
  def onDevelopBranch: Condition = OnBranch("develop", PatternKind.Exact)
  def onReleaseBranch: Condition = OnBranch("release-*", PatternKind.Wildcard)
  def onFeatureBranch: Condition = OnBranch("feature/*", PatternKind.Wildcard)
  def onHotfixBranch: Condition  = OnBranch("hotfix/*", PatternKind.Wildcard)

  // Common tag patterns
  def onVersionTag: Condition = OnTag("v*", PatternKind.Wildcard)

  // Aliases for common helpers
  val onPR: Condition = onPullRequest

  // Combinators
  def all(first: Condition, rest: Condition*): Condition =
    val combined = (first +: rest.toVector).flatMap {
      case And(cs) => cs.toVector
      case c       => Vector(c)
    }
    if combined.size == 1 then combined.head
    else And(NonEmptyVector.fromVectorUnsafe(combined)).simplify

  def any(first: Condition, rest: Condition*): Condition =
    val combined = (first +: rest.toVector).flatMap {
      case Or(cs) => cs.toVector
      case c      => Vector(c)
    }
    if combined.size == 1 then combined.head
    else Or(NonEmptyVector.fromVectorUnsafe(combined)).simplify

  def not(condition: Condition): Condition =
    Not(condition).simplify

  // Path helpers
  def onPathsChanged(first: String, rest: String*): Condition =
    OnPathsChanged(NonEmptyVector(first, rest.toVector))

  def onPathsNotChanged(first: String, rest: String*): Condition =
    OnPathsNotChanged(NonEmptyVector(first, rest.toVector))

  // Label helpers
  def hasAllLabels(first: String, rest: String*): Condition =
    HasAllLabels(NonEmptyVector(first, rest.toVector))

  def hasAnyLabel(first: String, rest: String*): Condition =
    HasAnyLabel(NonEmptyVector(first, rest.toVector))

  /** Simplifies a condition using logical rules */
  def simplify(c: Condition): Condition = c match
    // Base cases
    case Always | Never => c

    // Double negation
    case Not(Not(inner)) => simplify(inner)

    // Not with Always/Never
    case Not(Always) => Never
    case Not(Never)  => Always

    // And simplification
    case And(cs) =>
      val simplified = cs.map(simplify).toVector
      if simplified.contains(Never) then Never
      else
        val filtered = simplified.filterNot(_ == Always).distinct
        if filtered.isEmpty then Always
        else if filtered.size == 1 then filtered.head
        else And(NonEmptyVector.fromVectorUnsafe(filtered))

    // Or simplification
    case Or(cs) =>
      val simplified = cs.map(simplify).toVector
      if simplified.contains(Always) then Always
      else
        val filtered = simplified.filterNot(_ == Never).distinct
        if filtered.isEmpty then Never
        else if filtered.size == 1 then filtered.head
        else Or(NonEmptyVector.fromVectorUnsafe(filtered))

    // Not with And/Or (De Morgan's laws)
    case Not(And(cs)) => simplify(Or(cs.map(c => Not(c))))
    case Not(Or(cs))  => simplify(And(cs.map(c => Not(c))))
    // Other Not cases
    case Not(inner) => Not(simplify(inner))

    // Default - no simplification
    case _ => c

/** Extension methods for building conditions fluently */
extension (c: Condition)
  /** Logical AND combinator */
  def &&(other: Condition): Condition = (c, other) match
    case (Condition.Never, _) | (_, Condition.Never) => Condition.Never
    case (Condition.Always, o)                       => o
    case (c, Condition.Always)                       => c
    case (Condition.And(cs), Condition.And(os))      => Condition.And(cs ++ os.toVector)
    case (Condition.And(cs), o)                      => Condition.And(cs :+ o)
    case (c, Condition.And(os))                      => Condition.And(c +: os)
    case _                                           => Condition.And(NonEmptyVector.of(c, other))

  /** Logical OR combinator */
  def ||(other: Condition): Condition = (c, other) match
    case (Condition.Always, _) | (_, Condition.Always) => Condition.Always
    case (Condition.Never, o)                          => o
    case (c, Condition.Never)                          => c
    case (Condition.Or(cs), Condition.Or(os))          => Condition.Or(cs ++ os.toVector)
    case (Condition.Or(cs), o)                         => Condition.Or(cs :+ o)
    case (c, Condition.Or(os))                         => Condition.Or(c +: os)
    case _                                             => Condition.Or(NonEmptyVector.of(c, other))

  /** Logical NOT */
  def unary_! : Condition = c match
    case Condition.Always     => Condition.Never
    case Condition.Never      => Condition.Always
    case Condition.Not(inner) => inner
    case _                    => Condition.Not(c)
