package io.vamp.core.operation.workflow

import java.util.Properties

import akka.actor.{Actor, ActorRef}
import io.vamp.core.model.workflow.{ScheduledWorkflow, TimeTrigger}
import io.vamp.core.operation.workflow.WorkflowSchedulerActor.RunWorkflow
import org.quartz._
import org.quartz.impl.StdSchedulerFactory


trait WorkflowQuartzScheduler {
  this: Actor =>

  private lazy val scheduler = {
    val props = new Properties()
    props.setProperty("org.quartz.scheduler.instanceName", "workflow-scheduler")
    props.setProperty("org.quartz.threadPool.threadCount", "1")
    props.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore")
    props.setProperty("org.quartz.scheduler.skipUpdateCheck", "true")
    new StdSchedulerFactory(props).getScheduler
  }

  def quartzSchedule(scheduledWorkflow: ScheduledWorkflow) = scheduledWorkflow.trigger match {
    case TimeTrigger(pattern) =>

      quartzUnschedule(scheduledWorkflow)

      val job = {
        val data = new JobDataMap()
        data.put("message", RunWorkflow(scheduledWorkflow))
        data.put("actor", self)
        JobBuilder.newJob(classOf[QuartzJob])
          .usingJobData(data)
          .withIdentity(new JobKey(scheduledWorkflow.name))
          .build()
      }

      val trigger = {
        TriggerBuilder.newTrigger()
          .startNow()
          .withIdentity(new TriggerKey(s"${scheduledWorkflow.name}-trigger")).forJob(job)
          .withSchedule(org.quartz.CronScheduleBuilder.cronSchedule(pattern))
          .build()
      }

      scheduler.scheduleJob(job, trigger)

    case _ =>
  }

  def quartzUnschedule(scheduledWorkflow: ScheduledWorkflow) = scheduler.deleteJob(new JobKey(scheduledWorkflow.name))

  def quartzStart: (Unit => Unit) = { _ => scheduler.start() }

  def quartzShutdown: (Unit => Unit) = { _ => scheduler.shutdown() }
}

private class QuartzJob() extends Job {
  def execute(ctx: JobExecutionContext) {
    val data = ctx.getJobDetail.getJobDataMap
    data.get("actor").asInstanceOf[ActorRef] ! data.get("message")
  }
}