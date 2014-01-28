package com.inno.zxing.test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.inno.zxing.util.BufferedImageLuminanceSource;

public class TestDecode {
	public static void main(String[] args) throws NotFoundException, IOException {
		File file = new File("new.jpg");
		BufferedImage image = ImageIO.read(file);
		LuminanceSource source = new BufferedImageLuminanceSource(image);
		BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source ));
		Map<DecodeHintType, String> hints = new HashMap<DecodeHintType, String>();
		hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
		Result result = new MultiFormatReader().decode(bitmap, hints );
		
		System.out.println(result.getText());
	}
}
