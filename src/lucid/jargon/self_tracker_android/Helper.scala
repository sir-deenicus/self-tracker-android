package lucid.jargon.self_tracker_android

import android.widget.Toast
import android.view.View
import android.content.Context
import android.view.View.OnLongClickListener
import java.util.Date
import com.google.gson.Gson
import java.io.{FileReader, BufferedReader}
import scala.collection.mutable
import java.util

/**
 * Created with IntelliJ IDEA.
 * User: sir.deenicus
 * Date: 6/21/13
 * Time: 10:05 PM
 * To change this template use File | Settings | File Templates.
 */
class UsefulDate(time : java.util.Date) {
  def addHours(adj: Double) =  new java.util.Date(time.getTime() + (adj * 3600000).round)
}

object Helper {
	val dateWrite = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SZ")
	val dateRead = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")

  implicit def toListener[F](f: View => F) =
    new View.OnClickListener {
      def onClick(view: View) {
        f(view)
      }
    }

  def createToast(context: Context,  text : String, duration : Int = Toast.LENGTH_LONG) : Toast = {
    Toast.makeText(context, text, duration)
  }

  implicit def usefulDate(time : java.util.Date) = new UsefulDate(time)

  implicit def toRunnable[F](f: => F): Runnable =
    new Runnable() {
      def run() = f
    }

  def Round(x:Double) = ((x * 10).round:Double) / 10.0

	def Round2(x:Double, places : Int) = {
		val scale = Math.pow(10.0,places)
		((x * scale).round:Double) / scale }

	def subtractDateToHours(d1:Date, d2:Date):Double = (d1.getTime - d2.getTime)/3600000.0

	def subtractDateToSeconds(d1:Date, d2:Date):Double = (d1.getTime - d2.getTime)/1000.

	def doSaveActivities(a: java.util.ArrayList[Item], fname: String, gson : Gson) =  {
		val json = gson.toJson(a)
		val writer = new java.io.FileWriter(fname)
		writer.write(json)
		writer.close()
	}

	def writeAllString(fname: String, fdata : String, doAppend : Boolean = true) =  {
		val writer = new java.io.FileWriter(fname,doAppend)
		writer.append(fdata)
		writer.close()
	}

	def writeAllLines(fname: String, fdata : Array[String], doAppend : Boolean = true) =  {
		val writer = new java.io.FileWriter(fname,doAppend)
		fdata.foreach(s => {
			writer.append(s)
			writer.append("\n")
		})
		writer.close()
	}

	def readAllLines[T](fname : String) = {
		val br = new BufferedReader(new FileReader(fname))
		try {
			val sb = new StringBuilder()
			var line = br.readLine()

			while (line != null) {
				sb.append(line)
				sb.append("\n")
				line = br.readLine()}
			br.close()
			sb.toString();}

		finally {
			br.close()
		}}

	def strToDate(dstr:String) : Date ={
		val stripmicrosecs = dstr.substring(0,dstr.indexOf("."))
		dateRead.parse(stripmicrosecs)
	}

	def readAllLinesMap[T](f : String => T, fname : String) = {
		val br = new BufferedReader(new FileReader(fname))
		try {
			var line = br.readLine()
			val l = new java.util.ArrayList[T]()

			while (line != null) {
				l.add(f(line))
				line = br.readLine();}
			br.close()
			l}

		finally {
			br.close()
	}}
}
