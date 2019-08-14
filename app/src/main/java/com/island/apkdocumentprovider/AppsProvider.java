package com.island.apkdocumentprovider;
import android.content.*;
import android.content.pm.*;
import android.database.*;
import android.net.*;
import android.os.*;
import android.provider.*;
import android.provider.DocumentsContract.*;
import android.util.*;
import java.io.*;
import java.util.*;
import android.content.res.*;
import android.graphics.*;
public class AppsProvider extends DocumentsProvider
{
	private static final String APPS="/data/app";
	private static final String SYSTEM_APPS="/system/app";
	private static final String APK_MIME_TYPE="application/vnd.android.package-archive";
	private static final String[]DEFAULT_ROOT_PROJECTION=
	{Root.COLUMN_ROOT_ID,Root.COLUMN_FLAGS,Root.COLUMN_ICON,Root.COLUMN_TITLE,Root.COLUMN_DOCUMENT_ID,Root.COLUMN_MIME_TYPES};
	private static final String[] DEFAULT_DOCUMENT_PROJECTION=
	{Document.COLUMN_DOCUMENT_ID,Document.COLUMN_SIZE,Document.COLUMN_DISPLAY_NAME,Document.COLUMN_LAST_MODIFIED,Document.COLUMN_MIME_TYPE,Document.COLUMN_FLAGS};
	@Override
	public boolean onCreate()
	{
		Log.i(MainActivity.LOG_TAG,"Documents provider created");
		return true;
	}
	@Override
	public Cursor queryRoots(String[]projection)throws FileNotFoundException
	{
		try
		{
			Log.i(MainActivity.LOG_TAG,"Query Root: Projection="+Arrays.toString(projection));
			MatrixCursor result=new MatrixCursor(resolveRootProjection(projection));
			MatrixCursor.RowBuilder row=result.newRow();
			row.add(Root.COLUMN_ROOT_ID,APPS);
			row.add(Root.COLUMN_DOCUMENT_ID,APPS+"/");
			row.add(Root.COLUMN_ICON,R.drawable.ic_launcher);
			row.add(Root.COLUMN_FLAGS,Root.FLAG_SUPPORTS_SEARCH);
			row.add(Root.COLUMN_TITLE,"APK files");
			row.add(Root.COLUMN_MIME_TYPES,APK_MIME_TYPE);
			return result;
		}
		catch(Exception e)
		{
			String msg="Error querying roots";
			Log.e(MainActivity.LOG_TAG,msg,e);
			throw new FileNotFoundException(msg);
		}
	}
	@Override
	public Cursor queryDocument(String documentId,String[]projection)throws FileNotFoundException
	{
		try
		{
			Log.i(MainActivity.LOG_TAG,"Query Document: DocumentId="+documentId+" Projection="+Arrays.toString(projection));
			MatrixCursor result=new MatrixCursor(resolveDocumentProjection(projection));
			String pack=getPackage(documentId);
			String name;
			long size;
			long time;
			if(pack.isEmpty())
			{
				if(pack.startsWith(APPS))name="Apps";
				else name="System Apps";
				size=0;
				time=0;
			}
			else
			{
				PackageManager packageManager=getContext().getPackageManager();
				ApplicationInfo info=packageManager.getApplicationInfo(pack,PackageManager.GET_META_DATA);
				name=info.loadLabel(packageManager).toString();
			}
			putFileInfo(result.newRow(),new File(documentId),name);
			return result;
		}
		catch(Exception e)
		{
			String msg="Error querying child "+documentId;
			Log.e(MainActivity.LOG_TAG,msg,e);
			throw new FileNotFoundException(msg);
		}
	}
	@Override
	public Cursor queryChildDocuments(String parentDocumentId,String[]projection,String sortOrder)throws FileNotFoundException
	{
		try
		{
			Log.i(MainActivity.LOG_TAG,"Query Child Documents: ParentDocumentId="+parentDocumentId+" Projection="+projection+" SortOrder="+sortOrder);
			MatrixCursor result=new MatrixCursor(resolveDocumentProjection(projection));
			PackageManager packageManager=getContext().getPackageManager();
			List<ApplicationInfo>packages=packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
			String root;
			if(parentDocumentId.startsWith(APPS))
			{
				root=APPS;
				putFileInfo(result.newRow(),new File(SYSTEM_APPS),"System Apps");
			}
			else root=SYSTEM_APPS;
			for(ApplicationInfo packageInfo:packages)
			{
				String name=packageInfo.packageName;
				String source=packageInfo.sourceDir;
				if(source.startsWith(root))
				{
					putFileInfo(result.newRow(),new File(parentDocumentId+name),packageInfo.loadLabel(packageManager).toString());
				}
			}
			return result;
		}
		catch(Exception e)
		{
			String msg="Error querying childs of "+parentDocumentId;
			Log.e(MainActivity.LOG_TAG,msg,e);
			throw new FileNotFoundException(msg);
		}
	}
	@Override
	public ParcelFileDescriptor openDocument(String documentId,String mode,CancellationSignal signal)throws FileNotFoundException
	{
		try
		{
			Log.i(MainActivity.LOG_TAG,"Open Document: DocumentId="+documentId+" mode="+mode+" signal="+signal);
			int accessMode=ParcelFileDescriptor.parseMode(mode);
			boolean isWrite=(mode.indexOf('w')!=-1);
			File file=new File(documentId);
			file=new File(documentId.substring(0,documentId.length()-1)+"-1/base.apk");
			if(isWrite)
			{
				throw new UnsupportedOperationException("Write not supported");
			}
			return ParcelFileDescriptor.open(file,accessMode);
		}
		catch(Exception e)
		{
			String msg="Error opening document "+documentId;
			Log.e(MainActivity.LOG_TAG,msg,e);
			throw new FileNotFoundException(msg);
		}
	}
	@Override
	public AssetFileDescriptor openDocumentThumbnail(String documentId,Point sizeHint,CancellationSignal signal)throws FileNotFoundException
	{
		try
		{
			Log.i(MainActivity.LOG_TAG,"Open Document Thumbnail: DocumentId="+documentId+" sizeHint="+sizeHint+" signal="+signal);
			String pack=getPackage(documentId);
			PackageManager packageManager=getContext().getPackageManager();
			ApplicationInfo app=packageManager.getApplicationInfo(pack,PackageManager.GET_META_DATA);
			BitmapFactory.Options options=new BitmapFactory.Options();
			options.inJustDecodeBounds=true;
			BitmapFactory.decodeResource(packageManager.getResourcesForApplication(pack),app.icon,options);
			int width=options.outWidth;
			int height=options.outHeight;
			int sample=1;
			while(width>=sizeHint.x||height>=sizeHint.y)
			{
				sample*=2;
				width/=2;
				height/=2;
			}
			options.inSampleSize=sample;
			options.inJustDecodeBounds=false;
			Bitmap bitmap=BitmapFactory.decodeResource(packageManager.getResourcesForApplication(pack),app.icon,options);
			File dir=new File(getContext().getExternalCacheDir(),"icons");
			if(!dir.exists())dir.mkdir();
			final File file=new File(dir,app.name+".png");
			file.createNewFile();
			OutputStream output=new BufferedOutputStream(new FileOutputStream(file));
			bitmap.compress(Bitmap.CompressFormat.PNG,100,output);
			output.flush();
			output.close();
			ParcelFileDescriptor parcelFileDescriptor=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY,new Handler(getContext().getMainLooper()),new ParcelFileDescriptor.OnCloseListener()
				{
					@Override
					public void onClose(IOException exception)
					{
						file.delete();
					}
				});
			AssetFileDescriptor assetFileDescriptor=new AssetFileDescriptor(parcelFileDescriptor,0,AssetFileDescriptor.UNKNOWN_LENGTH);
			return assetFileDescriptor;
		}
		catch(Exception e)
		{
			String msg="Error opening document thumbnail "+documentId;
			Log.e(MainActivity.LOG_TAG,msg,e);
			throw new FileNotFoundException(msg);
		}
	}
    @Override
    public void deleteDocument(String documentId)throws FileNotFoundException
	{
        try
		{
			Log.i(MainActivity.LOG_TAG,"Delete document: documentId="+documentId);
			Uri packageUri=Uri.parse("package:"+getPackage(documentId));
            Intent uninstallIntent=new Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri);
            getContext().startActivity(uninstallIntent);
		}
		catch(Exception e)
		{
			String msg="Error deleting "+documentId;
			Log.e(MainActivity.LOG_TAG,msg,e);
			throw new FileNotFoundException(msg);
		}
    }
    @Override
    public String getDocumentType(String documentId)throws FileNotFoundException
	{
		try
		{
			Log.i(MainActivity.LOG_TAG,"Get document type: documentId="+documentId);
        	return"application/vnd.android.package-archive";
		}
		catch(Exception e)
		{
			String msg="Error getting type of "+documentId;
			Log.e(MainActivity.LOG_TAG,msg,e);
			throw new FileNotFoundException(msg);
		}
    }
	@Override
    public Cursor querySearchDocuments(String rootId,String query,String[]projection)throws FileNotFoundException
	{
        try
		{
			Log.i(MainActivity.LOG_TAG,"Query Child Documents: rootId="+rootId+" Query="+query+" Projection="+projection);
			MatrixCursor result=new MatrixCursor(resolveDocumentProjection(projection));
			PackageManager packageManager=getContext().getPackageManager();
			List<ApplicationInfo>packages=packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
			for(ApplicationInfo packageInfo:packages)
			{
				String name=packageInfo.packageName;
				if(name.contains(query))
				{
					putFileInfo(result.newRow(),new File(rootId+name),packageInfo.loadLabel(packageManager).toString());
				}
			}
			return result;
		}
		catch(Exception e)
		{
			String msg="Error searching "+query;
			Log.e(MainActivity.LOG_TAG,msg,e);
			throw new FileNotFoundException(msg);
		}
    }
	private static String[]resolveDocumentProjection(String[]projection)
	{
		if(projection==null)return DEFAULT_DOCUMENT_PROJECTION;
		else return projection;
	}
	private static String[]resolveRootProjection(String[]projection)
	{
		if(projection==null)return DEFAULT_ROOT_PROJECTION;
		else return projection;
	}
	private static String getPackage(String documentId)
	{
		String[]strings=documentId.split("/");
		if(strings.length<=3)return"";
		else return strings[3];
	}
	private static void putFileInfo(MatrixCursor.RowBuilder row,File file,String name)
	{
		int flags=0;
		String mime;
		File meta=new File(file.getPath()+"-1/base.apk");
		if(file.isDirectory())mime=Document.MIME_TYPE_DIR;
		else
		{
			mime=APK_MIME_TYPE;
			flags=Document.FLAG_SUPPORTS_DELETE|Document.FLAG_SUPPORTS_THUMBNAIL;
			row.add(Document.COLUMN_SIZE,meta.length());
		}
		row.add(Document.COLUMN_FLAGS,flags);
		row.add(Document.COLUMN_MIME_TYPE,mime);
		row.add(Document.COLUMN_DISPLAY_NAME,name);
		row.add(Document.COLUMN_DOCUMENT_ID,file.getPath()+"/");
		row.add(Document.COLUMN_LAST_MODIFIED,meta.lastModified());
	}
}
