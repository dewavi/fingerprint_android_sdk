package com.fys.password;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import com.fys.handprint.Protocol.Defined;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
/*
 * ��SD�����ļ���дҵ��
 * 	1����SD������ Defined.FilePathĿ¼
 * 	2����ָ��Ŀ¼��д�ļ�
 */
public class fileHelp {

	String TAG="fileHelp";
	Context context;
	String path;
	public fileHelp(Context c)
	{
		context=c;
		String SDPATH=Environment.getExternalStorageDirectory().getAbsolutePath();
		path=SDPATH+"/"+Defined.FilePath+"/";
		isExist(path);
		Log.e(TAG, path);
	}
	/**
	* 
	* @param path �ļ���·��
	*/
	public  void isExist(String path) {
	File file = new File(path);
	//�ж��ļ����Ƿ����,����������򴴽��ļ���
	if (!file.exists()) {
	file.mkdir();
	}
	}
	/**
	* 
	* @param path �ļ���·��
	* @file file �ļ�
	*/
	public void WriteFile(String name,byte[] buffer) 
	{
		File file = new File(path, name);
		try
		{
		FileOutputStream outStream = new FileOutputStream(file);
		outStream.write(buffer);
		outStream.close();
		}
		catch(Exception er)
		{
			Log.e(TAG, "д���ļ�ʧ�ܣ�"+er.toString());
		}
	}
	
}
