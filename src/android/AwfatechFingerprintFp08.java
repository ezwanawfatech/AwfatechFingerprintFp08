package cordova.plugin.awfatechfingerprint;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

//import copy
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import com.fgtit.device.Constants;
import com.fgtit.device.FPModule;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.support.v4.app.ActivityCompat;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Build;

public class AwfatechFingerprintFp08 extends CordovaPlugin {

    private static final int MY_PERMISSION_REQUEST_CODE = 10000;
	
	private FPModule fpm=new FPModule();
    
    private byte bmpdata[]=new byte[Constants.RESBMP_SIZE];
    private int bmpsize=0;
    private byte refdata[]=new byte[Constants.TEMPLATESIZE*4];
    private int refsize=0;
    private byte matdata[]=new byte[Constants.TEMPLATESIZE*4];
    private int matsize=0;
    
    private int worktype=0;

    private ArrayList<RefItem> mRefList;
    
	private TextView	tvDevStatu,tvFpStatu;
	private ImageView 	ivFpImage=null;
    private Intent intent;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("coolMethod")) {
            String message = args.getString(0);
            this.coolMethod(message, callbackContext);
            return true;
        }
        return false;
    }

    private void coolMethod(String message, CallbackContext callbackContext) {
        if (message != null && message.length() > 0) {
            callbackContext.success(message);
        } else {
            callbackContext.error("Expected one non-empty string argument.");
        }
    }

    @SuppressLint("HandlerLeak")
	private Handler mHandler = new Handler(){
        @Override
    	public void handleMessage(Message msg){
    		switch (msg.what){
    			case Constants.FPM_DEVICE:
    				switch(msg.arg1){
    				case Constants.DEV_OK:
    					tvFpStatu.setText("Open Device OK");
    					break;
    				case Constants.DEV_FAIL:
    					tvFpStatu.setText("Open Device Fail");
    					break;
    				case Constants.DEV_ATTACHED:
    					tvFpStatu.setText("USB Device Attached");
    					break;
    				case Constants.DEV_DETACHED:
    					tvFpStatu.setText("USB Device Detached");
    					break;
    				case Constants.DEV_CLOSE:
    					tvFpStatu.setText("Device Close");
    					break;
    				}
    				break;
    			case Constants.FPM_PLACE:
    				tvFpStatu.setText("Place Finger");
    				break;
    			case Constants.FPM_LIFT:
    				tvFpStatu.setText("Lift Finger");
    				break;
           	 	case Constants.FPM_GENCHAR:{
       	 			if(msg.arg1==1){
       	 				if(worktype==0){
       	 					tvFpStatu.setText("Generate Template OK");
       	 					matsize=fpm.GetTemplateByGen(matdata);       	 				

       	 					//if(fpm.MatchTemplate(refdata, refsize,matdata,matsize,80))
       	 					//	tvFpStatu.setText(String.format("Match OK"));
       	 					//else
       	 					//tvFpStatu.setText(String.format("Match Fail"));
							tvFpStatu.setText(String.format("Match Result:%d",MatchList()));
       	 					
       	 					writeStringToFile("mat.txt",Base64.encodeToString(matdata,0,matsize,Base64.DEFAULT));
       	 				}else{
       	 					tvFpStatu.setText("Enrol Template OK");
       	 					refsize=fpm.GetTemplateByGen(refdata);

							RefItem refitem=new RefItem();
							refitem.id=mRefList.size();
							System.arraycopy(refdata,0,refitem.template,0,refsize);
							mRefList.add(refitem);
       	 					
       	 					writeStringToFile("ref.txt",Base64.encodeToString(refdata,0,refsize,Base64.DEFAULT));
       	 				}
       	 			}else{
       	 				tvFpStatu.setText("Generate Template Fail");
       	 			}
       	 			}
       	 			break;
           	 	case Constants.FPM_NEWIMAGE:{
           	 		bmpsize=fpm.GetBmpImage(bmpdata);
       	 			Bitmap bm1=BitmapFactory.decodeByteArray(bmpdata, 0, bmpsize);
       	 			ivFpImage.setImageBitmap(bm1);
       	 			}
       	 			break; 
           	 	case Constants.FPM_TIMEOUT:
           	 		tvFpStatu.setText("Time Out");
           	 		break;
    		}
        }  
    };

    private int MatchList(){
		int score=0;
		int index=-1;
		for(int i=0;i<mRefList.size();i++){
			int sc=fpm.MatchTemplate(mRefList.get(i).template, mRefList.get(i).template.length,matdata,matdata.length);
			if(sc>60){
				if(sc>score){
					index=mRefList.get(i).id;
					score=sc;
				}
			}
		}
		return index;
	}

	@Override
	protected void onDestroy(){
		super.onDestroy();
		intent.setAction("android.intent.action.ChangeHotoffReceiver");
	}
	
    @Override
	protected void onResume() {
		super.onResume();		
		fpm.ResumeRegister();
		fpm.OpenDevice();
    }
    
    /*
	@Override
	protected void onPause() {		
		super.onPause();
		fpm.PauseUnRegister();
		fpm.CloseDevice();
	}
	*/

	@Override
	protected void onStop() {		
		super.onStop();
		fpm.PauseUnRegister();
		fpm.CloseDevice();
	}

	private void initView(){
		
		tvDevStatu=(TextView)findViewById(R.id.textView1);
		tvFpStatu=(TextView)findViewById(R.id.textView2);
		ivFpImage=(ImageView)findViewById(R.id.imageView1);
		
		final Button btn_enrol=(Button)findViewById(R.id.button1);
		final Button btn_capture=(Button)findViewById(R.id.button2);
		final Button btn_cancel=(Button)findViewById(R.id.button3);
		final Button btn_open=(Button)findViewById(R.id.button4);
		final Button btn_close=(Button)findViewById(R.id.button5);
				
		btn_enrol.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View v) {
				if(fpm.GenerateTemplate(4)){
					worktype=1;
				}else{
					Toast.makeText(MainActivity.this, "Busy", Toast.LENGTH_SHORT).show();
				}
			}
		});
		
		btn_capture.setOnClickListener(new View.OnClickListener(){
			@Override			
			public void onClick(View v) {
				if(fpm.GenerateTemplate(1)){
					worktype=0;
				}else{
					Toast.makeText(MainActivity.this, "Busy", Toast.LENGTH_SHORT).show();
				}
			}
		});
		
		btn_cancel.setOnClickListener(new View.OnClickListener(){
			@Override			
			public void onClick(View v) {
				fpm.Cancle();
				tvFpStatu.setText("Cancel");
			}
		});
		
		btn_open.setOnClickListener(new View.OnClickListener(){
			@Override			
			public void onClick(View v) {
				//fpm.ResumeRegister();
				fpm.OpenDevice();
			}
		});
		
		btn_close.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View v) {
				//fpm.PauseUnRegister();
				fpm.CloseDevice();
			}
		});
	}
	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    //Test   
    public void writeStringToFile(String filepath,String inputFileContext){
		String storageDir = Environment.getExternalStorageDirectory().toString();
		try {
			File file = new File(storageDir+"/"+filepath);
			FileOutputStream fos = new FileOutputStream(file);
			fos.write(inputFileContext.getBytes());
			fos.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

    }
    
    public String readStringFromFile(String filepath){
		String storageDir = Environment.getExternalStorageDirectory().toString();
		try {
			File file = new File(storageDir+"/"+filepath);
			FileInputStream fis=new FileInputStream(file);
			int length=fis.available();
			byte[] buffer = new byte[length];
			fis.read(buffer);
			String fileContent = new String(buffer);
			return fileContent;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
    }

    public void Test(){
    	String txt1=readStringFromFile("test1.txt");
    	byte[] test1=Base64.decode(txt1,Base64.DEFAULT);

		String txt2=readStringFromFile("test2.txt");
		byte[] test2=Base64.decode(txt2,Base64.DEFAULT);

		String txt3=readStringFromFile("test3.txt");
		byte[] test3=Base64.decode(txt3,Base64.DEFAULT);

		String result="";

		result=result+String.valueOf(fpm.MatchTemplate(test2, test2.length,test1,test1.length))+"  ";
		result=result+String.valueOf(fpm.MatchTemplate(test3, test3.length,test1,test1.length))+"  ";

		toast(result);
	}

	public void checkPermission() {
		/**
		 * 第 1 步: 检查是否有相应的权限
		 */
		boolean isAllGranted = checkPermissionAllGranted(
				new String[] {
						Manifest.permission.WRITE_EXTERNAL_STORAGE,
						Manifest.permission.READ_EXTERNAL_STORAGE
				}
		);
		// 如果这3个权限全都拥有, 则直接执行读取短信代码
		if (isAllGranted) {

			//toast("所有权限已经授权！");
			return;
		}

		/**
		 * 第 2 步: 请求权限
		 */
		// 一次请求多个权限, 如果其他有权限是已经授予的将会自动忽略掉
		ActivityCompat.requestPermissions(
				this,
				new String[] {
						Manifest.permission.WRITE_EXTERNAL_STORAGE,
						Manifest.permission.READ_EXTERNAL_STORAGE
				},MY_PERMISSION_REQUEST_CODE
		);
	}
	/**
	 * Semak sama ada anda mempunyai semua kebenaran yang ditentukan
	 */
	private boolean checkPermissionAllGranted(String[] permissions) {
		for (String permission : permissions) {
			if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
				// 只要有一个权限没有被授予, 则直接返回 false
				toast("检查权限");
				return false;
			}
		}
		return true;
	}

	/**
	 * Langkah 3: Minta hasil keputusan pengembalian izin
	 */
	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		if (requestCode == MY_PERMISSION_REQUEST_CODE) {
			boolean isAllGranted = true;

			// Tentukan sama ada semua kebenaran telah diberikan
			for (int grant : grantResults) {
				if (grant != PackageManager.PERMISSION_GRANTED) {
					isAllGranted = false;
					break;
				}
			}

			if (isAllGranted) {
				// Sekiranya semua kebenaran diberikan, jalankan kod SMS baca

			} else {
				// Kotak dialog pop timbul memberitahu pengguna mengapa kebenaran diperlukan, 
                //dan membimbing pengguna untuk membuka butang kebenaran secara manual dalam pengurusan hak aplikasi.
                // openAppDetails();
				toast("需要授权！");
			}
		}
	}

	public void toast(String content){
		Toast.makeText(getApplicationContext(),content,Toast.LENGTH_SHORT).show();
	}
}
