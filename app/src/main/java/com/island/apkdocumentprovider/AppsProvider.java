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
import android.content.pm.PackageManager.*;
public class AppsProvider extends DocumentsProvider
{
	private static final String INSTALLED_APPS="app";
	private static final String SYSTEM_APPS="system";
	private static final String APK_MIME_TYPE="application/vnd.android.package-archive";
	private static final String LOG_TAG="APK Document Provider";
	private static final String INSTALLED_APPS_DIR="/data/app";
	private static final String[]DEFAULT_ROOT_PROJECTION=
	{Root.COLUMN_ROOT_ID,Root.COLUMN_FLAGS,Root.COLUMN_ICON,Root.COLUMN_TITLE,Root.COLUMN_DOCUMENT_ID,Root.COLUMN_MIME_TYPES,Root.COLUMN_SUMMARY};
	private static final String[] DEFAULT_DOCUMENT_PROJECTION=
	{Document.COLUMN_DOCUMENT_ID,Document.COLUMN_SIZE,Document.COLUMN_DISPLAY_NAME,Document.COLUMN_LAST_MODIFIED,Document.COLUMN_MIME_TYPE,Document.COLUMN_FLAGS};
	@Override
	public boolean onCreate()
	{
		Log.i(LOG_TAG,"Documents provider created");
		//Return true because the initialization always succeeds
		return true;
	}
	@Override
	public Cursor queryRoots(String[]projection)throws FileNotFoundException
	{
		try
		{
			Log.i(LOG_TAG,"Query Root: Projection="+Arrays.toString(projection));
			//Add a single root because there is only one list of installed apps
			MatrixCursor result=new MatrixCursor(resolveRootProjection(projection));
			MatrixCursor.RowBuilder row=result.newRow();
			row.add(Root.COLUMN_ROOT_ID,INSTALLED_APPS);
			row.add(Root.COLUMN_DOCUMENT_ID,INSTALLED_APPS+"/");
			row.add(Root.COLUMN_ICON,R.drawable.ic_launcher);
			row.add(Root.COLUMN_FLAGS,Root.FLAG_SUPPORTS_SEARCH);
			row.add(Root.COLUMN_TITLE,getContext().getString(R.string.provider_name));
			row.add(Root.COLUMN_MIME_TYPES,APK_MIME_TYPE);
			int installed=getContext().getPackageManager().getInstalledApplications(0).size();
			row.add(Root.COLUMN_SUMMARY,installed+" "+getContext().getString(R.string.installed));
			return result;
		}
		catch(Exception e)
		{
			String msg="Error querying roots";
			Log.e(LOG_TAG,msg,e);
			throw new FileNotFoundException(msg);
		}
	}
	@Override
	public Cursor queryDocument(String documentId,String[]projection)throws FileNotFoundException
	{
		try
		{
			Log.i(LOG_TAG,"Query Document: DocumentId="+documentId+" Projection="+Arrays.toString(projection));
			//Add the infos of the selected document
			MatrixCursor result=new MatrixCursor(resolveDocumentProjection(projection));
			putFileInfo(result.newRow(),getContext().getResources(),getContext().getPackageManager(),documentId);
			return result;
		}
		catch(Exception e)
		{
			String msg="Error querying child "+documentId;
			Log.e(LOG_TAG,msg,e);
			throw new FileNotFoundException(msg);
		}
	}
	@Override
	public Cursor queryChildDocuments(String parentDocumentId,String[]projection,String sortOrder)throws FileNotFoundException
	{
		try
		{
			Log.i(LOG_TAG,"Query Child Documents: ParentDocumentId="+parentDocumentId+" Projection="+projection+" SortOrder="+sortOrder);
			//Add the infos of every package based on the type requested
			MatrixCursor result=new MatrixCursor(resolveDocumentProjection(projection));
			PackageManager packageManager=getContext().getPackageManager();
			List<PackageInfo>packages=packageManager.getInstalledPackages(PackageManager.GET_META_DATA);
			String root;
			if(getRoot(parentDocumentId).equals(INSTALLED_APPS))
			{
				//Add a folder to access the system apps and separe them from the user's apps
				root=INSTALLED_APPS;
				putFileInfo(result.newRow(),getContext().getResources(),getContext().getPackageManager(),SYSTEM_APPS+"/");
			}
			else root=SYSTEM_APPS;
			for(PackageInfo packageInfo:packages)
			{
				String name=packageInfo.packageName;
				if(root.equals(SYSTEM_APPS)^packageInfo.applicationInfo.sourceDir.startsWith(INSTALLED_APPS_DIR))
				{
					putFileInfo(result.newRow(),getContext().getResources(),getContext().getPackageManager(),parentDocumentId+name);
				}
			}
			return result;
		}
		catch(Exception e)
		{
			String msg="Error querying childs of "+parentDocumentId;
			Log.e(LOG_TAG,msg,e);
			throw new FileNotFoundException(msg);
		}
	}
	@Override
	public AssetFileDescriptor openDocumentThumbnail(String documentId,Point sizeHint,CancellationSignal signal)throws FileNotFoundException
	{
		try
		{
			Log.i(LOG_TAG,"Open Document Thumbnail: DocumentId="+documentId+" sizeHint="+sizeHint+" signal="+signal);
			//Load the resources of the app and load its icon as a bitmap
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
				//If the icon is too big, it reduces its sizes
				sample*=2;
				width/=2;
				height/=2;
			}
			options.inSampleSize=sample;
			options.inJustDecodeBounds=false;
			Bitmap bitmap=BitmapFactory.decodeResource(packageManager.getResourcesForApplication(pack),app.icon,options);
			if(bitmap!=null)
			{
				//Save the icon in a temporary file and return the parcel of the file
				File dir=new File(getContext().getExternalCacheDir(),"icons");
				if(!dir.exists())dir.mkdir();
				final File file=new File(dir,app.name+".png");
				file.createNewFile();
				OutputStream output=new BufferedOutputStream(new FileOutputStream(file));
				bitmap.compress(Bitmap.CompressFormat.PNG,100,output);
				bitmap.recycle();
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
			else 
			{
				Log.w(LOG_TAG,"No icon available for app "+app.name);
				return null;
			}
		}
		catch(Exception e)
		{
			String msg="Error opening document thumbnail "+documentId;
			Log.e(LOG_TAG,msg,e);
			throw new FileNotFoundException(msg);
		}
	}
	@Override
	public ParcelFileDescriptor openDocument(String documentId,String mode,CancellationSignal signal)throws FileNotFoundException
	{
		try
		{
			Log.i(LOG_TAG,"Open Document: DocumentId="+documentId+" mode="+mode+" signal="+signal);
			//Create a parcel of the apk file
			int accessMode=ParcelFileDescriptor.parseMode(mode);
			boolean isWrite=(mode.indexOf('w')!=-1);
			ApplicationInfo info=getContext().getPackageManager().getApplicationInfo(getPackage(documentId),PackageManager.GET_META_DATA);
			File file=new File(info.sourceDir);
			if(isWrite)
			{
				throw new UnsupportedOperationException("Write not supported");
			}
			return ParcelFileDescriptor.open(file,accessMode);
		}
		catch(Exception e)
		{
			String msg="Error opening document "+documentId;
			Log.e(LOG_TAG,msg,e);
			throw new FileNotFoundException(msg);
		}
	}
    @Override
    public void deleteDocument(String documentId)throws FileNotFoundException
	{
        try
		{
			Log.i(LOG_TAG,"Delete document: documentId="+documentId);
			//Start the dialog to uninstall the app
			Uri packageUri=Uri.parse("package:"+getPackage(documentId));
            Intent uninstallIntent=new Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri);
            getContext().startActivity(uninstallIntent);
		}
		catch(Exception e)
		{
			String msg="Error deleting "+documentId;
			Log.e(LOG_TAG,msg,e);
			throw new FileNotFoundException(msg);
		}
    }
	@Override
    public Cursor querySearchDocuments(String rootId,String query,String[]projection)throws FileNotFoundException
	{
        try
		{
			Log.i(LOG_TAG,"Query Search Documents: rootId="+rootId+" Query="+query+" Projection="+projection);
			//Search the apk based on the package or app name
			query=query.toLowerCase();
			MatrixCursor result=new MatrixCursor(resolveDocumentProjection(projection));
			PackageManager packageManager=getContext().getPackageManager();
			List<ApplicationInfo>packages=packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
			for(ApplicationInfo packageInfo:packages)
			{
				String name=packageInfo.loadLabel(packageManager).toString().toLowerCase();
				String pack=packageInfo.packageName;
				if(name.contains(query.toLowerCase())||pack.toLowerCase().contains(query))
				{
					putFileInfo(result.newRow(),getContext().getResources(),getContext().getPackageManager(),rootId+"/"+pack);
				}
			}
			return result;
		}
		catch(Exception e)
		{
			String msg="Error searching "+query;
			Log.e(LOG_TAG,msg,e);
			throw new FileNotFoundException(msg);
		}
    }
	@Override
    public String getDocumentType(String documentId)throws FileNotFoundException
	{
		try
		{
			Log.i(LOG_TAG,"Get document type: documentId="+documentId);
			//Return the mime type
			if(getPackage(documentId).isEmpty())return Document.MIME_TYPE_DIR;
        	else return APK_MIME_TYPE;
		}
		catch(Exception e)
		{
			String msg="Error getting type of "+documentId;
			Log.e(LOG_TAG,msg,e);
			throw new FileNotFoundException(msg);
		}
    }
	/**
	 * Return a non null projection
	 * @param projection The projection to resolve
	 * @return The projection itself or the default one
	 */
	private static String[]resolveDocumentProjection(String[]projection)
	{
		//If the proection is null return the default one
		if(projection==null)return DEFAULT_DOCUMENT_PROJECTION;
		else return projection;
	}
	/**
	 * Return a non null projection
	 * @param projection The projection to resolve
	 * @return The projection itself or the default one
	 */
	private static String[]resolveRootProjection(String[]projection)
	{
		//If the proection is null return the default one
		if(projection==null)return DEFAULT_ROOT_PROJECTION;
		else return projection;
	}
	/**
	 * Return the root of the document id
	 * @param documentId The documentId to analyze
	 * @return The root of the document id
	 * @throws FileNotFoundException If the documentId doesn't contain a root
	 */
	private static String getRoot(String documentId)throws FileNotFoundException
	{
		//Split the string and return the first part
		String[]strings=documentId.split("/");
		if(strings.length<=0)throw new FileNotFoundException("Error getting root of "+documentId);
		else return strings[0];
	}
	/**
	 * Return the package of the document id
	 * @param documentId The documentId to analyze
	 * @return The package of the document id
	 */
	private static String getPackage(String documentId)
	{
		//Split the string and return the second part that contains the package
		String[]strings=documentId.split("/");
		if(strings.length<=1)return"";
		else return strings[1];
	}
	/**
	 * Return the package infos of a package name
	 * @param packageManager The package manager to get the infos from
	 * @param packageName The package name to search
	 * @return The package infos
	 * @throws FileNotFoundException If the package isn't installed
	 */
	private static PackageInfo getPackage(PackageManager packageManager,String packageName)throws FileNotFoundException
	{
		try
		{
			//Uses the package manager to get the package info instance
			return packageManager.getPackageInfo(packageName,PackageManager.GET_META_DATA);
		}
		catch(PackageManager.NameNotFoundException e)
		{
			throw new FileNotFoundException("Package not found for name "+packageName);
		}
	}
	/**
	 * Add the package infos to the matrix
	 * @param row The row to add the infos
	 * @param resources The resources to get the strings to display to the user
	 * @param packageManager The package manager to use to load the infos
	 * @param documentId The document to add
	 * @throws FileNotFoundException If the documentId isn't well formed or if the package isn't installed
	 */
	private static void putFileInfo(MatrixCursor.RowBuilder row,Resources resources,PackageManager packageManager,String documentId)throws FileNotFoundException
	{
		//Add the properties of the package to the matrix
		int flags=0;
		String mime;
		String name;
		String packageName=getPackage(documentId);
		if(packageName.isEmpty())
		{
			mime=Document.MIME_TYPE_DIR;
			String root=getRoot(documentId);
			if(INSTALLED_APPS.equals(root))name=resources.getString(R.string.installed_apps);
			else name=resources.getString(R.string.system_apps);
		}
		else
		{
			mime=APK_MIME_TYPE;
			flags=Document.FLAG_SUPPORTS_DELETE|Document.FLAG_SUPPORTS_THUMBNAIL;
			PackageInfo info=getPackage(packageManager,packageName);
			File file=new File(info.applicationInfo.sourceDir);
			row.add(Document.COLUMN_SIZE,file.length());
			row.add(Document.COLUMN_LAST_MODIFIED,info.firstInstallTime);
			name=info.applicationInfo.loadLabel(packageManager).toString();
		}
		row.add(Document.COLUMN_FLAGS,flags);
		row.add(Document.COLUMN_MIME_TYPE,mime);
		row.add(Document.COLUMN_DISPLAY_NAME,name);
		row.add(Document.COLUMN_DOCUMENT_ID,documentId);
	}
}
