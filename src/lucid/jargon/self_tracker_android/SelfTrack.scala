package lucid.jargon.self_tracker_android

import scala.collection.JavaConversions._
import android.app.Activity
import android.os.Bundle
import android.view.{View, KeyEvent}
import com.google.gson.Gson
import android.widget.AutoCompleteTextView
import java.io.{File, BufferedReader, FileReader }
import Helper._

class Item  {
  var Item1 = ""
  var Item2 = ""
}

class SelfTrack extends Activity {
  /**
   * Called when the activity is first created.
   */

  def newItem(date:String, str:String):Item = {
      val i = new Item
      i.Item1 = date; i.Item2 = str
      i
  }

  def doSave(a: java.util.ArrayList[Item], fname: String, gson : Gson) =  {
       val json = gson.toJson(a)
       val writer = new java.io.FileWriter(fname)
       writer.write(json)
       writer.close()
  }

  def Round(x:Double) = ((x * 10).round:Double) / 10.0

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main)

    val suggestbox = findViewById(R.id.dropDown).asInstanceOf[AutoCompleteTextView]
    val viewList = findViewById(R.id.listView).asInstanceOf[android.widget.ListView]
    val button = findViewById(R.id.buttonAdd).asInstanceOf[android.widget.Button]

    val gson = new Gson()
    val itype = new com.google.gson.reflect.TypeToken[java.util.ArrayList[Item]]{}.getType

    val dateWrite = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SZ")
    val dateRead = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")

    val dir = android.os.Environment.getExternalStorageDirectory()
    val file = dir.getAbsolutePath() + "/Documents/activities.txt"
    val br = new BufferedReader(new FileReader(file))
    val actions = gson.fromJson[java.util.ArrayList[Item]](br, itype)
    br.close()

    val suggestions = new java.util.ArrayList(actions.map(item => item.Item2).toSet)
    val today = new java.util.Date()

    val rview = new java.util.ArrayList(
                     actions.take(100).map(item => {
                                val dstr = item.Item1
                                val stripmicrosecs = dstr.substring(0,dstr.indexOf("."))
                                val actdate = dateRead.parse(stripmicrosecs)

                                val (dayspassed, unitofTime) = { val delta : Double = (today.getTime() - actdate.getTime())
                                                                 val tdays = Round (delta / 86400000.0)
                                                                 if (tdays < 1.0) (Round(delta/3600000.0), "hours") else (tdays, "days")}
                                dayspassed.toString() + " " + unitofTime + " ago | " + item.Item2}))

    val lview = rview.clone().asInstanceOf[java.util.ArrayList[String]]
    val adapter = new android.widget.ArrayAdapter(this,android.R.layout.simple_expandable_list_item_1, lview)
    viewList.setAdapter(adapter)

    val adapterSuggest = new android.widget.ArrayAdapter(this,android.R.layout.simple_dropdown_item_1line, suggestions)
    suggestbox.setAdapter(adapterSuggest);

    suggestbox.addTextChangedListener(new android.text.TextWatcher() {
      def afterTextChanged(s : android.text.Editable){
        lview.clear()
        rview.foreach( s => { val keep = s contains suggestbox.getText().toString()
                              if (keep) lview.add(s) })
        adapter.notifyDataSetChanged()
      }
      def beforeTextChanged(s:java.lang.CharSequence, start:Int, count: Int, after : Int){}
      def onTextChanged(s:java.lang.CharSequence, start:Int, before : Int, count:Int){}
    })

    button.setOnClickListener((v : android.view.View) => {
      val (d,done) = (dateWrite.format(new java.util.Date()),suggestbox.getText().toString())
      actions.insert(0, newItem(d, done))
      doSave(actions, file, gson)
      val msg = (new java.util.Date()).toString() + " | " + done
      rview.insert(0, msg)
      lview.insert(0,msg)
      adapter.notifyDataSetChanged()
    })

    viewList.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
       override def onItemClick(parent: android.widget.AdapterView[_], view: View, position : Int, id : Long) {
          val str =  viewList.getItemAtPosition(position).asInstanceOf[String]
          val i_ = str.indexOf("hours")
          val (i, dayadj) = if (i_ == -1) (str.indexOf("days"), 24.0) else (i_ , 1.0)
          if(i != -1){
            val adj = java.lang.Double.parseDouble(str.substring(0,i - 1))
            val dateDone = today.addHours(-(adj * dayadj))
            val toasties = createToast(getApplicationContext(), str.split("\\|")(1).trim() + " was done at: " + dateDone)
            toasties.show()
          }
      }
    });
  }
}