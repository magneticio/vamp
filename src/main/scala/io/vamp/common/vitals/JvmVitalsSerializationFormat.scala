package io.vamp.common.vitals

import io.vamp.common.json.SerializationFormat
import org.json4s.FieldSerializer._
import org.json4s._

object JvmVitalsSerializationFormat extends SerializationFormat {
  override def fieldSerializers = super.fieldSerializers :+
    new JvmVitalsFieldSerializer() :+
    new OperatingSystemVitalsFieldSerializer() :+
    new RuntimeVitalsFieldSerializer() :+
    new MemoryVitalsFieldSerializer :+
    new ThreadVitalsFieldSerializer
}

class JvmVitalsFieldSerializer extends FieldSerializer[JvmVitals] {
  override val serializer: PartialFunction[(String, Any), Option[(String, Any)]] = renameTo("operatingSystem", "operating_system")
}

class OperatingSystemVitalsFieldSerializer extends FieldSerializer[OperatingSystemVitals] {
  override val serializer: PartialFunction[(String, Any), Option[(String, Any)]] =
    renameTo("availableProcessors", "available_processors") orElse renameTo("systemLoadAverage", "system_load_average")
}

class RuntimeVitalsFieldSerializer extends FieldSerializer[RuntimeVitals] {
  override val serializer: PartialFunction[(String, Any), Option[(String, Any)]] =
    renameTo("virtualMachineName", "virtual_machine_name") orElse
      renameTo("virtualMachineVendor", "virtual_machine_vendor") orElse
      renameTo("virtualMachineVersion", "virtual_machine_version") orElse
      renameTo("startTime", "start_time") orElse
      renameTo("upTime", "up_time")
}

class MemoryVitalsFieldSerializer extends FieldSerializer[MemoryVitals] {
  override val serializer: PartialFunction[(String, Any), Option[(String, Any)]] = renameTo("nonHeap", "non_heap")
}

class ThreadVitalsFieldSerializer extends FieldSerializer[ThreadVitals] {
  override val serializer: PartialFunction[(String, Any), Option[(String, Any)]] =
    renameTo("peakCount", "peak_count") orElse
      renameTo("daemonCount", "daemon_count") orElse
      renameTo("totalStartedCount", "total_started_count")
}
