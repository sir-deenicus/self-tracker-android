package lucid.jargon.self_tracker_android

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.location.{Location, LocationListener, LocationManager}
import android.widget.TextView
import android.hardware.{SensorEvent, SensorEventListener, Sensor, SensorManager}

/**
 * Created with IntelliJ IDEA.
 * User: sir.deenicus
 * Date: 1/8/14
 * Time: 11:53 PM
 * To change this template use File | Settings | File Templates.
 */
class Sensors extends Activity with SensorEventListener {
	var mSensorManager : SensorManager = null
	var mSensor : Sensor = null
	var mGrav : Sensor = null
	var mG : Array[Float] = null
	var mM : Array[Float] = null

	override def onResume() {
		mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
		mSensorManager.registerListener(this, mGrav, SensorManager.SENSOR_DELAY_NORMAL);
		super.onResume();
	}

	override def onPause() {
		mSensorManager.unregisterListener(this);
		super.onPause();
	}

	override def onSensorChanged(event : SensorEvent){
		val txtv = findViewById(R.id.textViewMAG).asInstanceOf[TextView]
		if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
			mG = event.values;

		if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
			mM = event.values;

		if (mG != null && mM != null) {
			val R : Array[Float] = new Array(9);
			val I : Array[Float] = new Array(9);
			val success = SensorManager.getRotationMatrix(R, I, mG, mM);
			if (success) {
				val orientation : Array[Float] = new Array(3);
				SensorManager.getOrientation(R, orientation);
				 // orientation contains: azimut, pitch and roll
				txtv.setText("incl: " + Helper.Round2(orientation(0),3))
			}
		}
	}

	override def onAccuracyChanged (sensor : Sensor, accuracy : Int){}

	override def onCreate(savedInstanceState: Bundle) {

		super.onCreate(savedInstanceState)
		setContentView(R.layout.sensors)

		val locationManager = getSystemService(Context.LOCATION_SERVICE).asInstanceOf[LocationManager]
		mSensorManager = getSystemService(Context.SENSOR_SERVICE).asInstanceOf[SensorManager];

		mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		mGrav = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

		// Define a listener that responds to location updates
		val locationListener = new LocationListener() {
			def onLocationChanged(location:Location) {
				val (lat, long, alt) = (location.getLatitude, location.getLongitude, location.getAltitude)
				val txtv = findViewById(R.id.textViewGPS).asInstanceOf[TextView]
				txtv.setText("loc: " + lat + "," + long + ", alt: " + alt)
			}

			def onStatusChanged(provider:String, status :Int, extras:Bundle) {}
			def onProviderEnabled(provider:String) {}
			def onProviderDisabled(provider:String) {}
		}

		try{
			locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000,	0, locationListener)
		  mSensorManager.registerListener(this,mSensor, SensorManager.SENSOR_DELAY_NORMAL)
		}
		catch{
			case ex =>
				val txtv = findViewById(R.id.textViewLoc).asInstanceOf[TextView]
				txtv.setText(ex.getMessage)}
	}
}
