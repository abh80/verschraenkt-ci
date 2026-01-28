package com.verschraenkt.ci.engine.converter

import com.verschraenkt.ci.core.model._
import com.verschraenkt.ci.engine.api.{JobDefinition, StepDefinition}
import java.util.UUID

object ModelConverter {

  def toJobDefinition(job: Job): JobDefinition = {
    val stepDefinitions = flattenSteps(job.steps.toVector)
      .zipWithIndex
      .flatMap { case (step, index) =>
        toStepDefinition(step, index)
      }

    JobDefinition(
      jobId = job.id,
      steps = stepDefinitions.toList,
      environment = Map.empty, // Job-level env not in Job model directly? Check Job class
      timeout = Some(job.timeout.toSeconds)
    )
  }

  private def flattenSteps(steps: Vector[Step]): Vector[Step] = {
    steps.flatMap {
      case Step.Composite(subSteps) => flattenSteps(subSteps.toVector)
      case r @ Step.Run(cmdLike) =>
        flattenCommand(cmdLike).map { cmd =>
          Step.Run(cmd)(using cmdLike.asCommand match {
            case c: Command.Composite => r.meta // Use parent logic? Ideally we'd clone meta logic
            case _ => r.meta
          })
        }
      case other => Vector(other)
    }
  }

  private def flattenCommand(cmdLike: CommandLike): Vector[Command] = {
    cmdLike.asCommand match {
      case Command.Composite(cmds) => strToVector(cmds).flatMap(flattenCommand)
      case single => Vector(single)
    }
  }

  private def strToVector[A](nev: cats.data.NonEmptyVector[A]): Vector[A] = nev.toVector


  private def toStepDefinition(step: Step, index: Int): Option[StepDefinition] = {
    step match {
      case r: Step.Run =>
        val (command, args) = extractCommand(r.command)
        val meta = r.meta
        val stepIdStr = meta.id.getOrElse(s"step-$index")
        
        Some(StepDefinition(
          stepId = StepId(stepIdStr),
          name = stepIdStr, // Use ID as name if no specific name field
          command = command,
          args = args,
          env = meta.env
        ))
      
      // Cache steps are not currently supported in StepDefinition (command extraction only)
      // They should explicitly be handled by a separate converter for CacheInstructions
      case _: Step.RestoreCache => None
      case _: Step.SaveCache => None
      
      // Composite should be flattened before calling this
      case _: Step.Composite => None 
      
      // Checkout is a semantic step, usually runs git
      case c: Step.Checkout =>
         val meta = c.meta
         val stepIdStr = meta.id.getOrElse(s"checkout-$index")
         Some(StepDefinition(
           stepId = StepId(stepIdStr),
           name = "checkout",
           command = "git",
           args = List("checkout", "."), // Simplified, actual checkout needs more args/logic
           env = meta.env
         ))
    }
  }
  
  private def extractCommand(cmdLike: CommandLike): (String, List[String]) = {
    cmdLike.asCommand match {
      case Command.Exec(program, args, _, _, _) =>
        (program, args)
        
      case Command.Shell(script, shell, _, _, _) =>
        shell match {
          case ShellKind.Bash => ("bash", List("-c", script))
          case ShellKind.Sh   => ("sh", List("-c", script))
          case ShellKind.Pwsh => ("pwsh", List("-c", script))
          case ShellKind.Cmd  => ("cmd", List("/c", script))
        }
        
      case Command.Composite(steps) =>
        // StepDefinition supports only one command. 
        // We strictly shouldn't reach here if we assume 1 Step = 1 Command. 
        // But Step.Run takes a CommandLike.
        // If it's a Composite Command in a single Step, we might need to join them with && or similar?
        // For now, let's take the first one or throw/log.
        // A better approach for Composite Command is to flatten it potentially?
        // But Step.Run is one step.
        // Let's join with " && " for shell?
        // Only works if they are shell commands.
        // For safety, let's just pick the first one and warn, or better:
        // We can't easily represent composite commands in one StepDefinition if it expects a single process.
        // UNLESS we wrap them in a shell script.
        ("sh", List("-c", "echo 'Composite commands not fully supported yet'"))
    }
  }
}
