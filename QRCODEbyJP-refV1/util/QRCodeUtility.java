package jp.sourceforge.qrcode.codec.util;

import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;

import javax.imageio.*;
//import javax.microedition.lcdui.*;
//import com.kddi.graphics.*;


/* 標準クラスライブラリ依存、プログラム言語依存になりそうな処理は
 * このクラスにまとめる
 * */

public class QRCodeUtility {
	
	public static int[][] parseImage(BufferedImage image) {
		int width = image.getWidth();
		int height = image.getHeight();
		int[][] result = new int[width][height];
		
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				result[x][y] = image.getRGB(x,y);
			}
		}
		return result;
	}
	
	public static int sqrt(int arg) {
		return (int)Math.sqrt(arg);
	}
	
	public static int[][] parseImage(String filename) {
		File file = new File(filename);
		BufferedImage image = null;
		try {
			image = ImageIO.read(file);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return parseImage(image);
	}
	
	public static int[][] parseImage(URL url) {
		BufferedImage image = null;
		try {
			image = ImageIO.read(url);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return parseImage(image);
	}
// for au by KDDI Profile Phase 3.0
//	/*画像の各ピクセルのRGBをint型の配列に展開する*/
//	public static int[][] parseImage(Image image) {
//		int width = image.getWidth();
//		int height = image.getHeight();
//		Image mutable = Image.createImage(width, height);
//		Graphics g = mutable.getGraphics();
//		g.drawImage(image, 0, 0, Graphics.TOP|Graphics.LEFT);
//		ExtensionGraphics eg = (ExtensionGraphics) g;
//		int[][] result = new int[width][height];
//		
//		for (int x = 0; x < width; x++) {
//			for (int y = 0; y < height; y++) {
//				result[x][y] = eg.getPixel(x, y);
//			}
//		}
//		return result;
//	}
//	
//	public static int[][] parseImage(byte[] imageData) {
//		return parseImage(Image.createImage(imageData, 0, imageData.length));
//	}
	

}
