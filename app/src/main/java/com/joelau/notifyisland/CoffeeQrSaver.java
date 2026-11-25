package com.joelau.notifyisland;

import android.content.*;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.*;

/** Save the original PNG; publish only after the complete file has been written. */
final class CoffeeQrSaver {
    static void save(Context context)throws IOException{
        ContentResolver resolver=context.getContentResolver();
        ContentValues values=new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME,"通知上岛-支持二维码-"+System.currentTimeMillis()+".png");
        values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/通知上岛");
        values.put(MediaStore.Images.Media.IS_PENDING,1);
        Uri uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
        if(uri==null)throw new IOException("Cannot create album image");
        boolean published=false;
        try{
            try(InputStream input=context.getResources().openRawResource(R.raw.coffee_qr_original);
                OutputStream output=resolver.openOutputStream(uri,"w")){
                if(output==null)throw new IOException("Cannot open album image");
                input.transferTo(output);
            }
            ContentValues ready=new ContentValues();ready.put(MediaStore.Images.Media.IS_PENDING,0);
            if(resolver.update(uri,ready,null,null)!=1)throw new IOException("Cannot publish album image");
            published=true;
        }finally{
            if(!published)try{resolver.delete(uri,null,null);}catch(RuntimeException ignored){}
        }
    }
}
