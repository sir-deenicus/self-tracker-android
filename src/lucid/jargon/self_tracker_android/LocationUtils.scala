package lucid.jargon.self_tracker_android

import android.os.{PowerManager, Bundle}
import android.app.Activity
import android.content.Context
import android.location.{LocationListener, Location, LocationManager}
import android.widget.{AutoCompleteTextView, CheckBox, TextView}
import android.speech.tts.TextToSpeech
import Helper._
import java.util.{TimeZone, Calendar, Date}
import scala.collection.mutable
import java.io.File
import java.util
import java.lang.Double
import scala.collection.JavaConversions._
import android.view.View.OnLongClickListener
import android.view.View

/**
 * Created with IntelliJ IDEA.
 * User: sir.deenicus
 * Date: 1/7/14
 * Time: 11:51 AM
 * To change this template use File | Settings | File Templates.
 */

class Geo(var Latitude:Double= 0.0, var Longitude:Double = 0.0)

class WalkDist(var DateStart : String = "", var DateEnd : String = "", var Dist : Double = 0.0)

class LocationUtils extends Activity with TextToSpeech.OnInitListener {
	var speakSynth : TextToSpeech =  null
  val Radius = 6371.0 //

	override def onDestroy() {
		// Don't forget to shutdown tts!
		if (speakSynth != null) {
			speakSynth.stop()
			speakSynth.shutdown()
		}
		super.onDestroy()
	}

	override def onInit(status:Int){}

	def toRad (deg:Double) = deg * Math.PI / 180.0
	def haversine (lat1:Double, long1:Double, lat2 : Double, long2 : Double) = {
		   val dLat = toRad(lat2 - lat1)
	     val dLong = toRad(long2 - long1)

		   val a =  Math.sin(dLat/2) * Math.sin(dLat/2) + Math.sin(dLong/2) * Math.sin(dLong/2) *
		            Math.cos(toRad(lat1)) * Math.cos(toRad(lat2))
			 val c = 2 * Math.atan2 (Math.sqrt(a), Math.sqrt(1-a))
		   Radius * c
	}

	def load(fileLocs : String, fileTimes : String) : (util.HashMap[String, Geo], util.ArrayList[String], Double) = {
		val localCalendar = Calendar.getInstance(TimeZone.getDefault)
		val day = localCalendar.get(Calendar.DAY_OF_YEAR)
		val year = localCalendar.get(Calendar.YEAR)

		def getDate(d:Date, dateType:Int)={
			val cal = Calendar.getInstance()
			cal.setTime(d)
			cal.get(dateType)
		}

		if(new File(fileLocs).isFile)   {
			val dists = readAllLinesMap(s => {
				     val spl = s.split(";;")
				     val dist = Double.parseDouble(spl(2))
						 val d1 = dateRead.parse(spl(0))
						(d1,dist)
			}, fileTimes)

			val tot = dists.filter((kv) => getDate(kv._1, Calendar.DAY_OF_YEAR) == day && getDate(kv._1, Calendar.YEAR) == year).foldLeft(0.0)((sum,pair) => sum + pair._2)

			val locs = readAllLinesMap(identity, fileLocs)
			val hmap = new util.HashMap[String,Geo]()
			val locnames = new util.ArrayList[String]()

			locs.foreach(s => {
				val spl = s.split(";;")
				locnames.add(spl(0))
				hmap.put(spl(0), new Geo(Latitude = Double.parseDouble(spl(1)), Longitude = Double.parseDouble(spl(2))))
			})
			(hmap,locnames, tot)
		}
		else (new util.HashMap(),new util.ArrayList[String](), 0.0)
	}

	var locationManager:LocationManager = null
	var locationListener : LocationListener = null

	def changeRate(urate:Int)={
		locationManager.removeUpdates(locationListener)
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, urate,	0, locationListener)
	}

	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.locationutils)
		speakSynth = new TextToSpeech(this,this)

		val dir = android.os.Environment.getExternalStorageDirectory
		val fileLocs = dir.getAbsolutePath + "/Documents/geolocmap.txt"
		val fileTimes = dir.getAbsolutePath + "/Documents/timedists.txt"

		val (locmap,locnames, tday) = load(fileLocs, fileTimes)
		var totWalkedDay = tday

	  var (lastLong, lastLat,totDist, lastUpdate) = (-1.0,-1.0, 0.0, 0)
		locationManager = getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]

		val mgr = getSystemService(Context.POWER_SERVICE).asInstanceOf[PowerManager]
		val wakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyWakeLock")

		val buttonStartWalk = findViewById(R.id.buttonStartWalk).asInstanceOf[android.widget.Button]
		val buttonEndWalk = findViewById(R.id.buttonEndWalk).asInstanceOf[android.widget.Button]
		val buttonCancelWalk = findViewById(R.id.buttonCancelWalk).asInstanceOf[android.widget.Button]
		val buttonAddLoc = findViewById(R.id.buttonAddLoc).asInstanceOf[android.widget.Button]
		val checkboxfreq = findViewById(R.id.checkBoxUpdateFreq).asInstanceOf[CheckBox]
		val txtv = findViewById(R.id.textViewLoc).asInstanceOf[TextView]
		val totWalkedView = findViewById(R.id.textViewTotWalked).asInstanceOf[TextView]
		val txtLoc = findViewById(R.id.autoCompleteTextViewLocs).asInstanceOf[AutoCompleteTextView]

		totWalkedView.setText("Total Walked Today: " + totWalkedDay + " meters")

		val avgspeeds = mutable.MutableList[Double]()
		var inputLoc = ""
		var lastTime = new Date()
		var lastTot = 0.0
		var isNum = false
		var reqDist = 0.0
		var iscalibrated = false
		var walkStarted = new Date()
		var targetGeo = new Geo()
		var addLocPresses = 0

		val adapterSuggest = new android.widget.ArrayAdapter(this,android.R.layout.simple_dropdown_item_1line, locnames)
		txtLoc.setAdapter(adapterSuggest)

		var (urate, speakrate) = (0, 0)

		def calcDistLeft() = {
			   if (isNum) reqDist - totDist else{1000.0 * haversine(lastLat, lastLong, targetGeo.Latitude, targetGeo.Longitude)}
		}

   // Define a listener that responds to location updates
	locationListener = new LocationListener() {
			def onLocationChanged(location:Location) {

				val (lat, long) = (location.getLatitude, location.getLongitude)
				val (diststr) = if ((lastLat,lastLong) == (-1,-1)) "n/a"
							          else {
													val d = haversine(lastLat,lastLong, lat,long)
						              val mdist_ = Math.floor(d * 1000)
					                val mdist =
						                   if (iscalibrated) mdist_
		                           else {
			                            iscalibrated = mdist_ < 2.0
							                    if (iscalibrated) changeRate(urate)
			                            lastTime = new Date()
			                            0.0 }

						              totDist += mdist
													mdist.toString}

				if (iscalibrated){
					 val vel = Round2(avgspeeds.foldLeft(0.0)(_+_) / avgspeeds.length,2)
					 val d = calcDistLeft()
					 val elapsed = Round(subtractDateToSeconds(new Date(), walkStarted)/60.0)
					 val estm = if (vel == 0.0) "unknown" else {Round2((d/vel)/60.0, 2) + " minutes to go."}
					 txtv.setText("loc: " + lat + "," + long + "; inst speed: " + vel + "m/sec dist: " + diststr + "m " + "Total Dist: " + totDist + "m. Dist Left: " + Round2(d,2) + ". Time left: " + estm + ". Elapsed Time: " + elapsed) }
			  else txtv.setText("please stay still. calibrating...claimed: " + diststr + "m/sec")

				lastUpdate += urate

				if (lastUpdate > speakrate) {
					val dleft = Round2(calcDistLeft(),3)
					val deltax = totDist - lastTot
					val deltat = subtractDateToSeconds(new Date(), lastTime)

					val v = deltax / deltat
					avgspeeds += v
					val a = Round2(avgspeeds.foldLeft(0.0)(_+_) / avgspeeds.length,2)
					val timeleft = Round((dleft / a)/ 60.0)
					val elapsed = Round(subtractDateToSeconds(new Date(), walkStarted)/60.0)
					val dmsg = if (dleft < 0.0) -dleft + " meters over target." else dleft + " meters to go."
					val dtime = if (a == 0.0) "unknown arrival time. " else if (timeleft < 0) "Over estimate by " + -timeleft + " minutes. " else "Arrival in " + timeleft + " minutes."
					speakSynth.speak("Total distance walked is : " + totDist + " meters. " + dmsg +  " Average speed is " + a + " m per second. " + dtime + " Elapsed time: " + elapsed + " minutes", TextToSpeech.QUEUE_ADD, null)

					lastUpdate = 0
					lastTot = totDist
				  lastTime = new Date()}

			    lastLong = long
			  	lastLat = lat}

			def onStatusChanged(provider:String, status :Int, extras:Bundle) {}
			def onProviderEnabled(provider:String) {}
			def onProviderDisabled(provider:String) {}
	}

		buttonAddLoc.setOnClickListener(
			(v : android.view.View) => {
				val s = txtLoc.getText.toString
				val haskey = locmap.containsKey(s)
				if((lastLat,lastLong) != (-1.0, -1.0) && (!haskey || addLocPresses == 1)) {
					if(addLocPresses > 0){
						locnames.remove(s)
						locmap.remove(s)
						adapterSuggest.remove(s)
					}
				  locnames.add(s)
					adapterSuggest.add(s)
				  adapterSuggest.notifyDataSetChanged()
				  locmap.put(s, new Geo(Latitude = lastLat, Longitude = lastLong))

					if(addLocPresses > 0){
						val lines = locmap.map(kv => {kv._1 + ";;" + kv._2.Latitude + ";;" + kv._2.Longitude}).toArray
					  writeAllLines(fileLocs, lines,doAppend = false)}
					else writeAllLines(fileLocs, Array(s + ";;" + lastLat + ";;" + lastLong))

					addLocPresses = 0
				  createToast(getApplicationContext, s + " added.").show()
				}
				else if (haskey && addLocPresses == 0){
					addLocPresses+=1
					createToast(getApplicationContext, "location exists already. press add again to replace").show()
				}
				else{
					createToast(getApplicationContext, "invalid co-ords. Set a short meter distance, wait for calibration then end walk.").show()
				}

			})

  txtLoc.addTextChangedListener(new android.text.TextWatcher() {
			def afterTextChanged(s : android.text.Editable){
				addLocPresses= 0
			}
			def beforeTextChanged(s:java.lang.CharSequence, start:Int, count: Int, after : Int){}
			def onTextChanged(s:java.lang.CharSequence, start:Int, before : Int, count:Int){}
		})

  buttonCancelWalk.setOnLongClickListener(new OnLongClickListener() {
	  def onLongClick(v: View): Boolean ={
		  if(iscalibrated){
			  txtv.setText("start walk to begin again.")
			  locationManager.removeUpdates(locationListener)
			  wakeLock.release()
			  iscalibrated = false}
		  else txtv.setText("start walk to begin. already stopped.")
	    true}})

		buttonCancelWalk.setOnClickListener(
			(v : android.view.View) => {createToast(getApplicationContext, "long press to cancel").show()})

	buttonEndWalk.setOnClickListener(
			(v : android.view.View) => {
				if(iscalibrated){
					txtv.setText(txtv.getText + "\n--------------\nstart walk to begin again.")
					locationManager.removeUpdates(locationListener)
					val wstart = dateWrite.format(walkStarted)
					val wend = dateWrite.format(new Date())

					writeAllLines(fileTimes,Array(wstart + ";;" + wend + ";;" + totDist))
					totWalkedDay += totDist
					totWalkedView.setText("Total Walked Today: " + totWalkedDay + " meters")//
					//dists.add(new WalkDist(DateStart = wstart, DateEnd = wend, Dists = totDist)
					wakeLock.release()
					iscalibrated = false}
				else txtv.setText("start walk to begin. already stopped.")})

	buttonStartWalk.setOnClickListener(
			(v : android.view.View) => {
				if(!iscalibrated){
					try{
						val inptxt = txtLoc.getText.toString
						inputLoc = if (inptxt == "") "100" else inptxt
						isNum = inputLoc.forall (c => c.isDigit)

						if (isNum || locmap.containsKey(inputLoc)){
							if (isNum) reqDist =  Integer.parseInt(inputLoc) else targetGeo = locmap.get(inputLoc)

							txtv.setText("acquiring sats...")
							wakeLock.acquire()
							avgspeeds.clear()
							lastTot = 0.0
							walkStarted = new Date()

							lastLong -1.0; lastLat = -1.0; totDist = 0.0; lastUpdate = 0
							if (checkboxfreq.isChecked) {
								urate = 2000
								speakrate = 30000}
							else{
								urate = 20000
								speakrate = 60000}

							locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0,	0, locationListener)}
					  else{
							createToast(getApplicationContext, "invalid loc").show()}}
				  catch{
						case ex : Throwable =>
							val txtv = findViewById(R.id.textViewLoc).asInstanceOf[TextView]
							txtv.setText(ex.getMessage)}}
			else createToast(getApplicationContext, "already running.").show()
			})
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
