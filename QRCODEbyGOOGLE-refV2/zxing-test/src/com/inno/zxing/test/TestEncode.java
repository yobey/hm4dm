package com.inno.zxing.test;

import java.io.File;
import java.util.Hashtable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.inno.zxing.util.MatrixToImageWriter;

public class TestEncode {

	public static void main(String[] args) throws Exception {
		String contents = "http://shop102612705.taobao.com";
		int width = 100;
		int height = 100;
		String format = "jpg";
		
		Hashtable<EncodeHintType, Object> hints = new Hashtable<EncodeHintType, Object>();
		hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);    
		hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
		BitMatrix bitMatrix = new MultiFormatWriter().encode(contents, BarcodeFormat.QR_CODE, width, height, hints);
		File outputFile = new File("new.jpg");
		MatrixToImageWriter.writeToFile(bitMatrix, format, outputFile);
	}

}
