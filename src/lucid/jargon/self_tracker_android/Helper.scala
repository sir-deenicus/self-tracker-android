package lucid.jargon.self_tracker_android

import android.widget.Toast
import android.view.View
import android.content.Context
import android.view.View.OnLongClickListener

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
  implicit def toListener[F](f: View => F) =
    new View.OnClickListener {
      def onClick(view: View) {
        f(view)
      }
    }

  def createToast(context: Context,  text : String, duration : Int = Toast.LENGTH_LONG) : Toast  = {
    Toast.makeText(context, text, duration)
  }

  implicit def usefulDate(time : java.util.Date) = new UsefulDate(time)

  implicit def toRunnable[F](f: => F): Runnable =
    new Runnable() {
      def run() = f
    }
}
