package lucid.jargon.self_tracker_android

import scala.collection.JavaConversions._
import android.app.Activity
import android.os.Bundle
import android.view.{MenuItem, View, Menu}
import com.google.gson.Gson
import android.widget.{ArrayAdapter, AdapterView, AutoCompleteTextView}
import java.io.{BufferedReader, FileReader }
import Helper._
import java.util.Date
import android.view.View.OnLongClickListener
import android.widget.AdapterView.OnItemLongClickListener
import android.content.Intent

class Item  {
  var Item1 = ""
  var Item2 = ""
}

class SelfTrack extends Activity {

	var checkPoint : Option[(java.util.Date,String)] = None
	val itype = new com.google.gson.reflect.TypeToken[java.util.ArrayList[Item]]{}.getType
	var gson: Gson = new Gson()
	var isconn = false
	var file = ""

	var actions : java.util.ArrayList[Item] = null
	var lview : java.util.ArrayList[String] = null
	var rview : java.util.ArrayList[String] = null
	var adapter : ArrayAdapter[String] = null

	def newItem(date:String, str:String):Item = {
		val i = new Item
		i.Item1 = date; i.Item2 = str
		i
	}

	def buildActionView (actions:java.util.ArrayList[Item]) ={
		new java.util.ArrayList(
			actions.take(100).map(item => {
				val actdate = strToDate(item.Item1)

				val (dayspassed, unitofTime) = { val delta : Double = new Date().getTime - actdate.getTime
					val tdays = Round (delta / 86400000.0)
					if (tdays < 1.0) (Round(delta/3600000.0), "hours") else (tdays, "days")}
				dayspassed.toString + " " + unitofTime + " ago | " + item.Item2}))
	}

	def doRefresh(){
		rview = buildActionView(actions)
		lview.clear()
		rview.foreach(s => lview.add(s))

		adapter.notifyDataSetChanged()
	}

	def launchIntent(command:String, data : String){
		val myIntent = new Intent()
		myIntent.setAction("lucid.jargon."+ command)
		myIntent.putExtra("lucid.jargon.dropbox-"+command, data)

		try{startActivityForResult(myIntent, 0)}
		catch {
			case ex =>
				val s = ex.getMessage
				createToast(getApplicationContext, "Dropbox Sync Tracker app not found.")}
	}

	/////////////////////EVENTS RESPONSE////////////////////////////////////////

	override def onOptionsItemSelected(item: MenuItem): Boolean = {
	    item.getItemId match {
		    case R.id.menu_location =>
			      val myIntent = new Intent(this, classOf[LocationUtils])
	          startActivity(myIntent)
		    /*case R.id.menu_upload => launchIntent("doUpload", gson.toJson(actions))
		    case R.id.refresh => launchIntent("doDownload","")*/
	    }
		true
	}

	 override def onActivityResult(requestCode:Int, resultCode:Int, data:Intent) {
		if (requestCode == 0) {
			if (resultCode == Activity.RESULT_OK) {
				if(data.hasExtra("downloaded")){
						val content = data.getStringExtra("downloaded")
						actions = gson.fromJson[java.util.ArrayList[Item]](content, itype)
					  doRefresh()
					doSaveActivities(actions, file, gson)
	     			createToast(getApplicationContext, "Refreshed").show()}
				else if(data.hasExtra("uploaded")) {createToast(getApplicationContext, "Uploaded").show()}
				else{createToast(getApplicationContext, "WTF?").show()}}
			else {
				createToast(getApplicationContext, "something went wrong").show()}}
		else {
			super.onActivityResult(requestCode, resultCode, data)}
	}

	override def onCreateOptionsMenu(menu: Menu):Boolean ={
		val menuInflater = getMenuInflater
		menuInflater.inflate(R.menu.menu, menu)
		true
	}

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main)

    val suggestbox = findViewById(R.id.dropDown).asInstanceOf[AutoCompleteTextView]
    val viewList = findViewById(R.id.listView).asInstanceOf[android.widget.ListView]
    val button = findViewById(R.id.buttonAdd).asInstanceOf[android.widget.Button]

	  val today = new Date()

	  val dir = android.os.Environment.getExternalStorageDirectory
	  file = dir.getAbsolutePath + "/Documents/activities.txt"

    val br = new BufferedReader(new FileReader(file))
    actions = gson.fromJson[java.util.ArrayList[Item]](br, itype)
    br.close()

    val suggestions = new java.util.ArrayList(actions.map(item => item.Item2).toSet)

    rview = buildActionView(actions)
    lview = rview.clone().asInstanceOf[java.util.ArrayList[String]]

	  //***********************************TEXTBOX**************************//

	  suggestbox.setThreshold(3)

    val adapterSuggest = new android.widget.ArrayAdapter(this,android.R.layout.simple_dropdown_item_1line, suggestions)
    suggestbox.setAdapter(adapterSuggest)

    suggestbox.addTextChangedListener(new android.text.TextWatcher() {
      def afterTextChanged(s : android.text.Editable){
        lview.clear()
        rview.foreach( s => { val keep = s contains suggestbox.getText.toString
                              if (keep) lview.add(s) })
        adapter.notifyDataSetChanged()
      }
      def beforeTextChanged(s:java.lang.CharSequence, start:Int, count: Int, after : Int){}
      def onTextChanged(s:java.lang.CharSequence, start:Int, before : Int, count:Int){}
    })

	  suggestbox.setOnLongClickListener(new OnLongClickListener() {
		  def onLongClick(v: View): Boolean ={suggestbox.setText(""); checkPoint=None; true}
	  })

	  //***********************************BUTTONS**************************//
    button.setOnClickListener((v : android.view.View) => {
	    val boxText = suggestbox.getText().toString()
      val (d,done) = checkPoint match{
	      case Some(placeHolder @ (oldTime, oldTxt)) if boxText == oldTxt  =>
		         checkPoint = None
		         val toasties = createToast(getApplicationContext, "Using version stored at: " + oldTime)
		         toasties.show()
		         placeHolder
	      case None => (new java.util.Date(),boxText)
      }
      actions.insert(0, newItem(dateWrite.format(d), done))
	    doSaveActivities(actions, file, gson)
      val msg = d.toString + " | " + done
      rview.insert(0, msg)
      lview.insert(0,msg)
      adapter.notifyDataSetChanged()
    })

	  button.setOnLongClickListener(new OnLongClickListener() {
		  def onLongClick(v: View): Boolean ={
			  val toasties = createToast(getApplicationContext, "Stored at current time. Hold Textbox to clear")
			  toasties.show()
			  checkPoint = Some(new java.util.Date(),suggestbox.getText().toString())
			  true}
	  })

	  //***********************************VIEW LIST**************************//

	  adapter = new android.widget.ArrayAdapter(this,android.R.layout.simple_expandable_list_item_1, lview)
	  viewList.setAdapter(adapter)

	  viewList.setOnItemLongClickListener(new OnItemLongClickListener() {
		  override def onItemLongClick(parent: AdapterView[_], view: View, position: Int, id: Long): Boolean = {
			  doRefresh()
			  true
		  }
	  })

    viewList.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
       override def onItemClick(parent: android.widget.AdapterView[_], view: View, position : Int, id : Long) {
          val str =  viewList.getItemAtPosition(position).asInstanceOf[String]
	        val did = str.split("\\|")(1).trim()
          val i_ = str.indexOf("hours")
          val (i, dayadj) = if (i_ == -1) (str.indexOf("days"), 24.0) else (i_ , 1.0)

          if(i != -1){
            val adj = java.lang.Double.parseDouble(str.substring(0,i - 1))
            val dateDone = today.addHours(-(adj * dayadj))
            val toasties = createToast(getApplicationContext, did + " was done at: " + dateDone)
            toasties.show()
          }
	       else {
	          val sel = actions(position)
	          val hoursago = Round(subtractDateToHours(new Date(),strToDate(sel.Item1)))
	          val toasties = createToast(getApplicationContext, hoursago.toString() + " hours ago.")
	          toasties.show()
          }

	       if(suggestbox.getText.length() > 0) suggestbox.setText(did)
      }
    })
  }
}