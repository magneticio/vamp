package io.vamp.common.vitals

import java.lang.management.ManagementFactory

case class JvmVitals(operatingSystem: OperatingSystemVitals, runtime: RuntimeVitals, memory: MemoryVitals, threads: ThreadVitals)

case class OperatingSystemVitals(name: String, architecture: String, version: String, availableProcessors: Double, systemLoadAverage: Double)

case class RuntimeVitals(process: String, virtualMachineName: String, virtualMachineVendor: String, virtualMachineVersion: String, startTime: Long, upTime: Long)

case class MemoryVitals(heap: MemoryUsageVitals, nonHeap: MemoryUsageVitals)

case class MemoryUsageVitals(init: Long, max: Long, committed: Long, used: Long)

case class ThreadVitals(count: Long, peakCount: Long, daemonCount: Long, totalStartedCount: Long)

trait JmxVitalsProvider {

  private lazy val memory = ManagementFactory.getMemoryMXBean
  private lazy val thread = ManagementFactory.getThreadMXBean
  private lazy val runtime = ManagementFactory.getRuntimeMXBean
  private lazy val operatingSystem = ManagementFactory.getOperatingSystemMXBean

  def jvmVitals(): JvmVitals = JvmVitals(
    OperatingSystemVitals(operatingSystem.getName, operatingSystem.getArch, operatingSystem.getVersion, operatingSystem.getAvailableProcessors, operatingSystem.getSystemLoadAverage),
    RuntimeVitals(runtime.getName, runtime.getVmName, runtime.getVmVendor, runtime.getVmVersion, runtime.getStartTime, runtime.getUptime),
    MemoryVitals(
      MemoryUsageVitals(memory.getHeapMemoryUsage.getInit, memory.getHeapMemoryUsage.getMax, memory.getHeapMemoryUsage.getCommitted, memory.getHeapMemoryUsage.getUsed),
      MemoryUsageVitals(memory.getNonHeapMemoryUsage.getInit, memory.getNonHeapMemoryUsage.getMax, memory.getNonHeapMemoryUsage.getCommitted, memory.getNonHeapMemoryUsage.getUsed)
    ),
    ThreadVitals(thread.getThreadCount, thread.getPeakThreadCount, thread.getDaemonThreadCount, thread.getTotalStartedThreadCount)
  )
}

