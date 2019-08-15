package com.island.apkdocumentprovider;
import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import android.provider.*;
import android.view.*;
public class MainActivity extends Activity
{
	public static final String LOG_TAG="APKDocumentProvider";
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
	}
	public void addAccount(View v)
	{
		startActivity(new Intent(Settings.ACTION_SYNC_SETTINGS));
	}
	public void openAppSettings(View v)
	{
		String packageName=getPackageName();
		Intent intent=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",packageName,null));
		intent.addCategory(Intent.CATEGORY_DEFAULT);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(intent);
	}
	public void openSaf(View v)
	{
		Intent intent=new Intent();
		intent.setComponent(new ComponentName("com.android.documentsui", "com.android.documentsui.FilesActivity"));
		startActivity(intent);
	}
}
