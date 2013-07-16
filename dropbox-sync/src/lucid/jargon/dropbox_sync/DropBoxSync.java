package lucid.jargon.dropbox_sync;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.dropbox.sync.android.DbxAccountManager;
import com.dropbox.sync.android.DbxFile;
import com.dropbox.sync.android.DbxFileSystem;
import com.dropbox.sync.android.DbxPath;

public class DropBoxSync extends Activity {
    /**
     * I sure as hell can't be fussed to untangle the proguard bullshit of getting this library to work with Scala
     */
    DbxAccountManager mDbxAcctMgr = null;
    Boolean isconn = false;
    ProgressBar spins = null;
    Bundle extras = null;
    TextView messages = null;

    String rkey="";
    String rdata="";

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 0) {
            if (resultCode == Activity.RESULT_OK) {
                isconn = true;
                Toast.makeText(getApplicationContext(), "connected", Toast.LENGTH_LONG);
                handleCommand();
            } else {
                Toast.makeText(getApplicationContext(), "Failed with dropbbox", Toast.LENGTH_LONG);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        spins = (ProgressBar) findViewById(R.id.progressBar);
        messages = (TextView) findViewById(R.id.lblMessage);

        spins.setVisibility(View.INVISIBLE);

        extras = getIntent().getExtras();
        if (extras == null) {
            messages.setText("You should not be here");
            return;
        }

        mDbxAcctMgr = DbxAccountManager.getInstance(getApplicationContext(), getResources().getString(R.string.apikey),
                getResources().getString(R.string.apisecret));

        isconn = mDbxAcctMgr.hasLinkedAccount();

        if(isconn){messages.setText("Already Connected.");}
        else{mDbxAcctMgr.startLink(this, 0);}

        handleCommand();
    }

    @Override
    public void finish() {
        Intent data = new Intent();
        data.putExtra(rkey, rdata);
        setResult(RESULT_OK, data);
        super.finish();
    }

    void handleCommand(){
        String downop = extras.getString("lucid.jargon.dropbox-doDownload");
        if(downop != null){
            try{
                DbxFileSystem dbxFs = DbxFileSystem.forAccount(mDbxAcctMgr.getLinkedAccount());
                DbxFile file = dbxFs.open(new DbxPath(DbxPath.ROOT, "activities.txt"));
                spins.setVisibility(View.VISIBLE);
                String content = file.readString();
                file.close();
                rkey = "downloaded";
                rdata = content;
                finish();
            }
            catch(Exception ex){messages.setText(ex.getMessage());}
        }
        else{
            String upop = extras.getString("lucid.jargon.dropbox-doUpload");
            if(upop != null){
                spins.setVisibility(View.VISIBLE);
                messages.setText("Please wait, uploading...");
                new Thread(new Runnable() {
                    public void run() {
                        try{
                            DbxFileSystem dbxFs = DbxFileSystem.forAccount(mDbxAcctMgr.getLinkedAccount());
                            DbxFile file = dbxFs.open(new DbxPath(DbxPath.ROOT, "activities.txt"));
                            String dat = extras.getString("lucid.jargon.dropbox-doUpload");
                            file.writeString(dat);
                            file.close();
                            rkey = "uploaded";
                            rdata = "";
                            finish();
                        }
                        catch(Exception ex){messages.setText(ex.getMessage());}
                    }
                }).start();
            }
        }
    }
}

