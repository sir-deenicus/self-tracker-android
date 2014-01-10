package lucid.jargon.self_tracker_android

import android.os.{PowerManager, Bundle}
import android.app.Activity
import android.content.Context
import android.location.{LocationListener, Location, LocationManager}
import android.widget.{AutoCompleteTextView, CheckBox, TextView}
import android.speech.tts.TextToSpeech
import Helper._
import java.util.Date
import scala.collection.mutable

/**
 * Created with IntelliJ IDEA.
 * User: sir.deenicus
 * Date: 1/7/14
 * Time: 11:51 AM
 * To change this template use File | Settings | File Templates.
 */

class Geo {var Latitude = 0.0; var Longitude = 0.0}

class WalkDist {var Datestart = ""; var DateEnd = ""; var Dist = 0.0}

class LocationUtils extends Activity with TextToSpeech.OnInitListener {
	var speakSynth : TextToSpeech =  null
  val Radius = 6371. //

	override def onDestroy() {
		// Don't forget to shutdown tts!
		if (speakSynth != null) {
			speakSynth.stop()
			speakSynth.shutdown()
		}
		super.onDestroy()
	}

	override def onInit(status:Int){}

	def toRad (deg:Double) = deg * Math.PI / 180.
	def haversine (lat1:Double, long1:Double, lat2 : Double, long2 : Double) = {
		   val dLat =  toRad(lat2 - lat1)
	     val dLong =  toRad(long2 - long1)

		   val a =  Math.sin(dLat/2) * Math.sin(dLat/2) + Math.sin(dLong/2) * Math.sin(dLong/2) *
		            Math.cos(toRad(lat1)) * Math.cos(toRad(lat2))
			 val c = 2 * Math.atan2 (Math.sqrt(a), Math.sqrt(1-a))
		   Radius * c
	}

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.locationutils)
		speakSynth = new TextToSpeech(this,this)

		var (lastLong, lastLat,totDist, lastUpdate) = (-1.0,-1.0, 0.0, 0)
		val locationManager = getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]

		val mgr = getSystemService(Context.POWER_SERVICE).asInstanceOf[PowerManager];
		val wakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakeLock");

		val buttonStartWalk = findViewById(R.id.buttonStartWalk).asInstanceOf[android.widget.Button]
		val buttonEndWalk = findViewById(R.id.buttonEndWalk).asInstanceOf[android.widget.Button]
		val buttonAddLoc = findViewById(R.id.buttonAddLoc).asInstanceOf[android.widget.Button]
		val checkboxfreq = findViewById(R.id.checkBoxUpdateFreq).asInstanceOf[CheckBox]
		val txtv = findViewById(R.id.textViewLoc).asInstanceOf[TextView]
		val txtLoc = findViewById(R.id.autoCompleteTextViewLocs).asInstanceOf[AutoCompleteTextView]
		val avgspeeds = mutable.MutableList[Double]()

		var inputLoc = ""
		var lastTime = new Date()
		var lastTot = 0.0
		var isNum = false
		var reqDist = 0.
		var iscalibrated = false

		var (urate, speakrate) = (0, 0)

		def calcDistLeft() = {
			   if (isNum) reqDist - totDist else 0.
		}

   // Define a listener that responds to location updates
		val locationListener = new LocationListener() {
			def onLocationChanged(location:Location) {

				val (lat, long) = (location.getLatitude, location.getLongitude)
				val (diststr) = if ((lastLat,lastLong) == (-1,-1)) ("n/a")
							          else {
													val d = haversine(lastLat,lastLong, lat,long)
						              val mdist_ = Math.floor(d * 1000)
					                val mdist =
						                   if (iscalibrated) mdist_
		                           else {
			                            iscalibrated = mdist_ < 2.
			                            lastTime = new Date();
			                            0. }

						              totDist += mdist
													(mdist.toString)}

				if (iscalibrated){
					 val v = location.getSpeed
					 txtv.setText("loc: " + lat + "," + long + "; speed: " + v + "m/sec dist: " + diststr + "m " + "Total Dist: " + totDist + "m") }
			  else txtv.setText("please stay still. calibrating...claimed: " + diststr + "m/sec")

				lastUpdate += urate

				if (lastUpdate > speakrate) {
					val dleft = calcDistLeft()
					val deltax = totDist - lastTot
					val deltat = subtractDateToSeconds(new Date(), lastTime)

					val v = deltax / deltat
					avgspeeds += v
					val a = Round(avgspeeds.foldLeft(0.)(_+_) / avgspeeds.length)
					val timeleft = Round((dleft / a)/ 60.)

					val dmsg = if (dleft < 0.) -dleft + " meters over target." else dleft + " meters to go."
					val dtime = if (a == 0.0) "unknown arrival time. " else if (timeleft < 0) "Over estimate. by " + -timeleft + " minutes" else "Arrival in " + timeleft + " minutes."
					speakSynth.speak("Total distance walked is : " + totDist + " meters. " + dmsg +  " Average speed is " + a + " m per second. " + dtime, TextToSpeech.QUEUE_ADD, null)

					lastUpdate = 0
					lastTot = totDist
				  lastTime = new Date()}

			    lastLong = long
			  	lastLat = lat}

			def onStatusChanged(provider:String, status :Int, extras:Bundle) {}
			def onProviderEnabled(provider:String) {}
			def onProviderDisabled(provider:String) {}
	}

	buttonEndWalk.setOnClickListener(
			(v : android.view.View) => {
				if(iscalibrated){
					txtv.setText("start walk to begin again.")
					locationManager.removeUpdates(locationListener)
					wakeLock.release()
					iscalibrated = false}
				else txtv.setText("start walk to begin. already stopped.")})

	buttonStartWalk.setOnClickListener(
			(v : android.view.View) => {
				try{
					txtv.setText("acquiring sats...")
					wakeLock.acquire();
					avgspeeds.clear()
					lastTot = 0.0
					inputLoc = txtLoc.getText.toString
					isNum = inputLoc.forall (c => c.isDigit)

					if (isNum) reqDist =  Integer.parseInt(inputLoc)

					lastLong -1.; lastLat = -1.; totDist = 0.0; lastUpdate = 0
					if (checkboxfreq.isChecked()) {
						urate = 2000
						speakrate =  30000}
					else {
						urate = 30000
						speakrate = 30000}
					locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, urate,	0, locationListener)}

				catch{
					case ex =>
						val txtv = findViewById(R.id.textViewLoc).asInstanceOf[TextView]
						txtv.setText(ex.getMessage)}})
	}
}

/*
public class Main extends Activity {
	private TextView batteryTxt;
	private BroadcastReceiver mBatInfoReceiver = new BroadcastReceiver(){
		@Override
		public void onReceive(Context ctxt, Intent intent) {
			int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
			batteryTxt.setText(String.valueOf(level) + "%");
		}
	};

	@Override
	public void onCreate(Bundle b) {
		super.onCreate(b);
		setContentView(R.layout.main);
		contentTxt = (TextView) this.findViewById(R.id.batteryTxt);
		this.registerReceiver(this.mBatInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
	}
}*/
