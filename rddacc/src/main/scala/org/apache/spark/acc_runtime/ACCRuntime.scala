package org.apache.spark.acc_runtime

import java.io._
import scala.util.matching.Regex
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

import org.apache.spark._
import org.apache.spark.{Partition, TaskContext}
import org.apache.spark.rdd._
import org.apache.spark.storage._
import org.apache.spark.scheduler._
import org.apache.spark.broadcast._

class ACCRuntime(sc: SparkContext) extends Logging {

  // Note: Cannot guarantee it is unique
  val appSignature: Int = Math
    .abs(("""\d+""".r findAllIn sc.applicationId)
    .addString(new StringBuilder).toLong.toInt)

  var BroadcastList: List[Broadcast_ACC[_]] = List()

  def stop() = {
    if (BroadcastList.length == 0)
      logInfo("No broadcast block to be released")
    else {
      // FIXME: Currently we use the fixed port number (1027) for all managers
      val WorkerList: Array[(String, Int)] = (sc.getExecutorStorageStatus)
        .map(w => w.blockManagerId.host)
        .distinct
        .map(w => (w, 1027))

      logInfo("Workers (" + WorkerList.length + "): " + WorkerList.map(w => w._1).mkString(", "))

      val msg = DataTransmitter.buildMessage(AccMessage.MsgType.ACCBROADCAST)
  
      for (e <- BroadcastList) {
        DataTransmitter.addBroadcastData(msg, e.brdcst_id)
      }

      for (worker <- WorkerList) {
        try {
          val workerIP = Util.getIPByHostName(worker._1)
          if (!workerIP.isDefined)
            throw new RuntimeException("Cannot resolve host name " + worker._1)
          val transmitter = new DataTransmitter(workerIP.get, worker._2)
          transmitter.send(msg)
          val revMsg = transmitter.receive()
          if (revMsg.getType() == AccMessage.MsgType.ACCFINISH)
            logInfo("Successfully release " + BroadcastList.length + " broadcast blocks from Manager " + worker._1)
          else
            logInfo("Fail to release broadcast blocks from Manager " + worker._1)
        }
        catch {
          case e: Throwable =>
            val sw = new StringWriter
            e.printStackTrace(new PrintWriter(sw))
            logInfo("Fail to release broadcast data from Manager " + worker._1 + ": " + sw.toString)
        }
      }
    }
    sc.stop
  }

  def wrap[T: ClassTag](rdd : RDD[T]) : ShellRDD[T] = {
    new ShellRDD[T](appSignature, rdd)
  }

  def wrap[T: ClassTag](bd : Broadcast[T]) : Broadcast_ACC[T] = {
    val newBrdcst = new Broadcast_ACC[T](appSignature, bd)
    BroadcastList = BroadcastList :+ newBrdcst
    newBrdcst
  }
}

