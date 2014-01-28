/*
 * 作成日： 2004/09/12
 *
 * TODO この生成されたファイルのテンプレートを変更するには次を参照。
 * ウィンドウ ＞ 設定 ＞ Java ＞ コード・スタイル ＞ コード・テンプレート
 */
package jp.sourceforge.qrcode.codec;

import java.util.Vector;

import jp.sourceforge.qrcode.codec.data.QRCodeSymbol;
import jp.sourceforge.qrcode.codec.ecc.BCH15_5;
import jp.sourceforge.qrcode.codec.ecc.ReedSolomon;
import jp.sourceforge.qrcode.codec.exception.AlignmentPatternEdgeNotFoundException;
import jp.sourceforge.qrcode.codec.exception.DecodingFailedException;
import jp.sourceforge.qrcode.codec.exception.FinderPatternNotFoundException;
import jp.sourceforge.qrcode.codec.exception.IllegalDataBlockException;
import jp.sourceforge.qrcode.codec.exception.SymbolNotFoundException;
import jp.sourceforge.qrcode.codec.exception.VersionInformationException;
import jp.sourceforge.qrcode.codec.reader.QRCodeDataBlockReader;
import jp.sourceforge.qrcode.codec.reader.QRCodeImageReader;
import jp.sourceforge.qrcode.codec.util.DebugCanvas;

/**
 * @author Owner
 *
 * TODO この生成された型コメントのテンプレートを変更するには次を参照。
 * ウィンドウ ＞ 設定 ＞ Java ＞ コード・スタイル ＞ コード・テンプレート
 */
public class QRCodeDecoder {
	//QRCodeImageReader reader;
	int internalScale;
	QRCodeSymbol symbol;
	DebugCanvas canvas;
	//デコーダ本体
	public QRCodeDecoder() {
		internalScale = 2;
		canvas = DebugCanvas.getCanvas();
	}
	
	public String decode(int[][] image) throws DecodingFailedException{
		canvas.println("Decoding started.");
		try {
			symbol = getQRCodeSymbol(image);
		} catch (SymbolNotFoundException e) {
			e.printStackTrace();
			throw new DecodingFailedException();
		}
		canvas.println("Created QRCode symbol.");
		//int ratio = canvas.getWidth() / symbol.getWidth();
		//canvas.drawMatrix(symbol , ratio);
		canvas.println("Reading symbol.");
		boolean[] formatInformation = getFormatInformation(symbol);
		symbol.setFormatInformation(formatInformation);
		canvas.println("Version: " + symbol.getVersionReference());
		String maskPattern = Integer.toString(symbol.getMaskPatternReferer() ,2);
		int length = maskPattern.length();
		for (int i = 0; i < 3 - length; i++)
			maskPattern = "0" + maskPattern;
		
		canvas.println("Mask pattern: " + maskPattern);
		canvas.println("Unmasking.");
		unmask(symbol);
		//canvas.drawMatrix(symbol, 4);
		int[] blocks = getBlocks(symbol);
		canvas.println("Correcting data errors.");
		int[] dataBlocks = getCorrectedDataBlocks(blocks);
		String decodedString = "";
		try {
			decodedString = getDecodedString(dataBlocks, symbol.getVersion());
		} catch (IllegalDataBlockException e) {
			e.printStackTrace();
			throw new DecodingFailedException();
		}
//		return new QRCodeContent(decodedString);
		canvas.println("Decoding finished.");
		return decodedString;
	}
	
	boolean[][] processImage(int[][] image) {
		imageToGrayScale(image);
		boolean[][] bitmap = grayScaleToBitmap(image);
		//boolean[][] bitmapEx = extendBitmap(bitmap, internalScale);
		return bitmap;
	}
	
	void imageToGrayScale(int[][] image) {
		for (int y = 0; y < image[0].length; y++) {
			for (int x = 0; x < image.length; x++) {
				int r = image[x][y] >> 16 & 0xFF;
				int g = image[x][y] >> 8 & 0xFF;
				int b = image[x][y] & 0xFF;
				int m = (r * 30 + g * 59 + b * 11) / 100;
				image[x][y] = m;
			}
		}
	}
	
	boolean[][] grayScaleToBitmap_(int[][] grayScale) {
		int[][] middle = findAreaMiddle(grayScale);
		int[] minmax = findMinMax(grayScale);
		boolean[][] bitmap = new boolean[grayScale.length][grayScale[0].length];
		int halftone = (minmax[0] + minmax[1]) / 2;

		for (int y = 0; y < grayScale[0].length; y++) {
			for (int x = 0; x < grayScale.length; x++) {
				bitmap[x][y] = (grayScale[x][y] < halftone) ? true : false;
			}
		}
		
		return bitmap;
	}
	boolean[][] grayScaleToBitmap(int[][] grayScale) {
		int[][] middle = findAreaMiddle(grayScale);
		int sqrtNumArea = middle.length;
		int areaWidth = grayScale.length / sqrtNumArea;
		int areaHeight = grayScale[0].length / sqrtNumArea;
		boolean[][] bitmap = new boolean[grayScale.length][grayScale[0].length];

		for (int ay = 0; ay < sqrtNumArea; ay++) {
			for (int ax = 0; ax < sqrtNumArea; ax++) {
				for (int dy = 0; dy < areaHeight; dy++) {
					for (int dx = 0; dx < areaWidth; dx++) {
						bitmap[areaWidth * ax + dx][areaHeight * ay + dy] = (grayScale[areaWidth * ax + dx][areaHeight * ay + dy] < middle[ax][ay]) ? true : false;
					}
				}
			}
		}
		return bitmap;
	}
	
	int[] findMinMax(int[][] image) {
		int tempMin = Integer.MAX_VALUE;
		int tempMax = Integer.MIN_VALUE;
		for (int y = 0; y < image[0].length; y++) {
			for (int x = 0; x < image.length; x++) {
				if (image[x][y] < tempMin)
					tempMin = image[x][y];
				else if (image[x][y] > tempMax)
					tempMax = image[x][y];
			}
		}
		return new int[] {tempMin, tempMax};
	}
	
	int[][] findAreaMiddle(int[][] image) {
		final int numSqrtArea = 4;
		//4x4のエリアごとの明るさの中間値((min + max) / 2)を出す
		int areaWidth = image.length / numSqrtArea;
		int areaHeight = image[0].length / numSqrtArea;
		int[][][] minmax = new int[numSqrtArea][numSqrtArea][2];
		for (int ay = 0; ay < numSqrtArea; ay++) {
			for (int ax = 0; ax < numSqrtArea; ax++) {
				minmax[ax][ay][0] = 0xFF;
				for (int dy = 0; dy < areaHeight; dy++) {
					for (int dx = 0; dx < areaWidth; dx++) {
						int target = image[areaWidth * ax + dx][areaHeight * ay + dy];
						if (target < minmax[ax][ay][0]) minmax[ax][ay][0] = target;
						if (target > minmax[ax][ay][1]) minmax[ax][ay][1] = target;
					}
				}
				//minmax[ax][ay][0] = (minmax[ax][ay][0] + minmax[ax][ay][1]) / 2;
			}
		}
		int[][] middle =  new int[numSqrtArea][numSqrtArea];
		for (int ay = 0; ay < numSqrtArea; ay++) {
			for (int ax = 0; ax < numSqrtArea; ax++) {
				middle[ax][ay] = (minmax[ax][ay][0] + minmax[ax][ay][1]) / 2;
				//System.out.print(middle[ax][ay] + ",");
			}
			//System.out.println("");
		}
		//System.out.println("");

		return middle;
	}
	
	boolean[][] extendBitmap(boolean[][] bitmap, int scale) {
		boolean[][] bitmap2x = new boolean[bitmap.length * 2][bitmap[0].length * 2];
		for (int y = 0; y < bitmap[0].length; y++) {
			for (int x = 0; x < bitmap.length; x++) {
				if (bitmap[x][y] == true) {
					for (int sx = 0; sx < scale; sx++)
						for (int sy = 0; sy < scale; sy++)
							bitmap2x[x * scale + sx][y * scale + sy] = true;
				}
					
			}
		}
		return bitmap2x;
	}

	QRCodeSymbol getQRCodeSymbol(int[][] image) throws SymbolNotFoundException {

		//canvas.println("Creating binary matrix.");
 		//BinaryMatrix binaryImage = new BinaryMatrix(imageData);
		//canvas.println("Drawing matrix.");
 		//canvas.drawMatrix(binaryImage);
 		//canvas.println("Reading matrix");
 		canvas.println("Creating bitmap.");
		boolean[][] bitmap = processImage(image);
		QRCodeImageReader reader = new QRCodeImageReader();

		QRCodeSymbol symbol = null;
		try {
			symbol = reader.getQRCodeSymbol(bitmap, internalScale);
		} catch (FinderPatternNotFoundException e) {
			throw new SymbolNotFoundException();
		} catch (VersionInformationException e2) {
			throw new SymbolNotFoundException();
		} catch (AlignmentPatternEdgeNotFoundException e3) {
			throw new SymbolNotFoundException();
		}
		return symbol;
	}
	
	boolean[] getFormatInformation(QRCodeSymbol qRCodeMatrix) {
		boolean[] modules = new boolean[15];

		//マトリックスから形式情報部分取り出し
		for (int i = 0; i <= 5; i++)
			modules[i] = qRCodeMatrix.getElement(8, i);
		
		modules[6] = qRCodeMatrix.getElement(8, 7);
		modules[7] = qRCodeMatrix.getElement(8, 8);
		modules[8] = qRCodeMatrix.getElement(7, 8);
		
		for (int i = 9; i <= 14; i++)
			modules[i] = qRCodeMatrix.getElement(14 - i, 8);
		
		//XOR演算でマスク処理
		int maskPattern = 0x5412;
		
		for (int i = 0; i <= 14; i++) {
			boolean xorBit = false;
			if (((maskPattern >>> i) & 1) == 1)
				xorBit = true;
			else
				xorBit = false;
			
			if (modules[i] == xorBit) //ビットシフトした後の一桁目を見る
				modules[i] = false;
			else
				modules[i] = true;
		}
		//int a[] = {0,1,2,4,8,3,6,12,11,5,10,7,14,15,13,9,1};
		//System.out.println("debug");
		//printBit("formatInfo", modules);
		
		//エラー訂正
		BCH15_5 corrector = new BCH15_5(modules);
		boolean[] output = corrector.correct();
		int numError = corrector.getNumCorrectedError();
		if (numError > 0)
			canvas.println(String.valueOf(numError) + " format errors corrected.");
		boolean[] formatInformation = new boolean[5];
		for (int i = 0; i < 5; i++)
			formatInformation[i] = output[10 + i];
		
		return formatInformation;
		
	}
	
	void unmask(QRCodeSymbol symbol) {
		int maskPatternReferer = symbol.getMaskPatternReferer();
		
		//マスクパターン生成
		boolean[][] maskPattern = generateMaskPattern(symbol);

		int size = symbol.getWidth();
		
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				if (maskPattern[x][y] == true) {
					symbol.reverseElement(x, y);
				}
			}
		}
	}
	
	boolean[][] generateMaskPattern(QRCodeSymbol symbol) {
		int maskPatternReferer = symbol.getMaskPatternReferer();
		
		//マスクパターン生成
		int width = symbol.getWidth();
		int height = symbol.getHeight();
		boolean[][] maskPattern = new boolean[width][height];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if (symbol.isInFunctionPattern(x, y))
					continue;
				switch (maskPatternReferer) {
				case 0: // 000
					if ((x + y) % 2 == 0)
						maskPattern[x][y] = true;
					break;
				case 1: // 001
					if (y % 2 == 0)
						maskPattern[x][y] = true;
					break;
				case 2: // 010
					if (x % 3 == 0)
						maskPattern[x][y] = true;
					break;
				case 3: // 011
					if ((x + y) % 3 == 0)
						maskPattern[x][y] = true;
					break;
				case 4: // 100
					if ((x / 3 + y / 2) % 2 == 0)
						maskPattern[x][y] = true;
					break;
				case 5: // 101
					if ((x * y) % 2 + (x * y) % 3 == 0)
						maskPattern[x][y] = true;
					break;
				case 6: // 110
					if (((x * y) % 2 + (x * y) % 3) % 2 == 0)
						maskPattern[x][y] = true;
					break;
				case 7: // 111
					if (((x * y) % 3 + (x + y) % 2) % 2 == 0)
						maskPattern[x][y] = true;
					break;
				}
			}
		}
		return maskPattern;
	}
	
	int[] getBlocks(QRCodeSymbol symbol) {
		int width = symbol.getWidth();
		//System.out.println("SymbolWidth:" + Integer.toString(symbol.getWidth()));
		//System.out.println("SymbolHeight:" + Integer.toString(symbol.getHeight()));
		int height = symbol.getHeight();
		int x = width - 1;
		int y = height - 1;
		Vector codeBits = new Vector();
		Vector codeWords = new Vector();
		int tempWord = 0;
		int figure = 7;
		int isNearFinish = 0;
		final boolean READ_UP = true;
		final boolean READ_DOWN = false;
		boolean direction = READ_UP;
		
		do {
			//canvas.drawPoint(new Point(x * 4 +8 , y * 4 + 47), Color.RED);
			codeBits.addElement(new Boolean(symbol.getElement(x, y)));
			//System.out.println(Integer.toString(codeBits.size()));
			//canvas.drawPoint(new Point(x*3 + 120,y*3 + 190), Color.RED);
			//int ratio = canvas.getWidth() / symbol.getWidth();
			//int offsetX = (canvas.getWidth() - symbol.getWidth() * ratio) / 2;
			//int offsetY = (canvas.getHeight() - symbol.getHeight() * ratio) / 2;
			//canvas.drawPoint(new Point(offsetX + x * ratio + 3, offsetY + y * ratio + 3), 0xFF0000);
			if (symbol.getElement(x, y) == true) {
				tempWord += 1 << figure;
			}
			//System.out.println(new Point(x, y).toString() + " " + symbol.getElement(x, y));
			figure--;
			if (figure == -1) {
				codeWords.addElement(new Integer(tempWord));
				//System.out.print(codeWords.size() + ": ");
				//System.out.println(tempWord);
				figure = 7;
				tempWord = 0;
			}
			//次に読むモジュールを決定する
			do {
				if (direction == READ_UP) {
					if ((x + isNearFinish) % 2 == 0) //二列のうち右側なら
						x--; //左側
					else { 
						if (y > 0) { //上に進める
							x++;
							y--;
						}
						else { //進めない
							x--; //方向転換し
							if (x == 6){
								x--;
								isNearFinish=1; // 縦のタイミングパターンを通過することによって判定を変える
							}
							direction = READ_DOWN;
						}
					}			
				}
				
				else {
					if ((x + isNearFinish) % 2 == 0) //二列のうち左側なら
						x--; 
					else {
						if (y < height - 1) {
							x++;
							y++;
						}
						else {
							x--;
							if (x == 6){
								x--;
								isNearFinish=1;
							}
							direction = READ_UP;
						}
					}				
				}
			} while (symbol.isInFunctionPattern(x, y));

		} while (x != -1);
		
		int[] gotWords = new int[codeWords.size()];
		for (int i = 0; i < codeWords.size(); i++) {
			Integer temp = (Integer)codeWords.elementAt(i);
			gotWords[i] = temp.intValue();
		}
		return gotWords; 
	}

	int[] getCorrectedDataBlocks(int[] blocks) {
		int numErrors = 0;
		//System.out.println(":");
		//System.out.println("blockLength: " + blocks.length);
		int version = symbol.getVersion();
		//System.out.println("Version: " + version);
		int errorCollectionLevel = symbol.getErrorCollectionLevel();
		//System.out.println("errorCollectionLevel:" + errorCollectionLevel);
		int dataCapacity = symbol.getDataCapacity();
		int[]  dataBlocks = new int[dataCapacity];
		//System.out.println("dataCapacity: " + dataCapacity);
		int numErrorCollectionCode = symbol.getNumErrorCollectionCode();
		//System.out.println("numErrorCollectionCode:" + numErrorCollectionCode);
		int numRSBlocks = symbol.getNumRSBlocks();
		int eccPerRSBlock = numErrorCollectionCode / numRSBlocks;
		//System.out.println("numRSBlocks: " + numRSBlocks);
		if (numRSBlocks == 1) {
			//[TODO]エラー訂正処理
//			for (int k = 0; k < blocks.length; k++) {
//				System.out.print(String.valueOf(blocks[k]) + ",");
//			}
//			System.out.println("");
//			System.out.println("numWords="+String.valueOf(blocks.length));

			ReedSolomon corrector = new ReedSolomon(blocks);
			corrector.correct();
			numErrors += corrector.getNumCorrectedErrors();
			if (numErrors > 0)
				canvas.println(String.valueOf(numErrors) + " data errors corrected.");
			else
				canvas.println("No errors found.");				
			return blocks;
		}
		else  { //RSブロックが2つ以上のため、データブロック並び替えあり
			int numLongerRSBlocks = dataCapacity % numRSBlocks;
			
			if (numLongerRSBlocks == 0) { //RSブロックは1種類
				int lengthRSBlock = dataCapacity / numRSBlocks;
				int[][] RSBlocks = new int[numRSBlocks][lengthRSBlock];
				//RSブロックを得る
				for (int i = 0; i < numRSBlocks; i++) {
					//System.out.println("i = " + i);
					//for (int j = 0; j < lengthRSBlock  - numErrorCollectionCode / numRSBlocks ; j++) {
					for (int j = 0; j < lengthRSBlock; j++) {
								//System.out.println("j = " + j);
					//try {
							RSBlocks[i][j] = blocks[j * numRSBlocks + i];
						//} catch (ArrayIndexOutOfBoundsException e) {}
					}
					//[TODO]エラー訂正処理
//					for (int k = 0; k < RSBlocks[i].length; k++) {
//						System.out.print(String.valueOf(RSBlocks[i][k]) + ",");
//					}
					//System.out.println("");
					//System.out.println("numWords="+String.valueOf(RSBlocks[i].length));

					ReedSolomon corrector = new ReedSolomon(RSBlocks[i]);
					corrector.correct();
					numErrors += corrector.getNumCorrectedErrors();

				}
				//データ部分のみ抜き出す
				int p = 0;
				for (int i = 0; i < numRSBlocks; i++) {
					for (int j = 0; j < lengthRSBlock - eccPerRSBlock; j++) {
						dataBlocks[p++] = RSBlocks[i][j];
					}
				}
			}
			else { //RSブロックは2種類
				int lengthShorterRSBlock = dataCapacity / numRSBlocks;
				//System.out.println("lengthShorterRSBlock : " + lengthShorterRSBlock);
				int lengthLongerRSBlock = dataCapacity / numRSBlocks + 1;
				//System.out.println("lengthLongerRSBlock: " + lengthLongerRSBlock);
				int numShorterRSBlocks = numRSBlocks - numLongerRSBlocks;
				//System.out.println("numShorterRSBlocks: " + numShorterRSBlocks);
				//System.out.println("numLongerRSBlocks: " + numLongerRSBlocks);
				int[][] shorterRSBlocks = new int[numShorterRSBlocks][lengthShorterRSBlock];
				int[][] longerRSBlocks = new int[numLongerRSBlocks][lengthLongerRSBlock];
				for (int i = 0; i < numRSBlocks; i++) {
					//System.out.println("i = " + i);
					if (i < numShorterRSBlocks) { 
						//短い方のRSブロックを得る
						//for (int j = 0; j < lengthShorterRSBlock - numErrorCollectionCode / numRSBlocks ; j++) {
						int mod = 0;

						for (int j = 0; j < lengthShorterRSBlock; j++) {
									//System.out.println(" j = " + j);
							if (j == lengthShorterRSBlock - eccPerRSBlock) mod = numLongerRSBlocks;
							//System.out.print(String.valueOf(j * numRSBlocks + i + mod) + ",");
							shorterRSBlocks[i][j] = blocks[j * numRSBlocks + i + mod];
						}
						//System.out.println("");
						//[TODO]エラー訂正処理
						
//						for (int k = 0; k < shorterRSBlocks[i].length; k++) {
//							System.out.print(String.valueOf(shorterRSBlocks[i][k]) + ",");
//						}
						//System.out.println("");
						//System.out.println("numWords="+String.valueOf(shorterRSBlocks[i].length));

						
						ReedSolomon corrector = new ReedSolomon(shorterRSBlocks[i]);
						corrector.correct();
						numErrors += corrector.getNumCorrectedErrors();

					}
					else { 
						//System.out.println("Debug" + String.valueOf(numShorterRSBlocks));

						//長い方のRSブロックを得る
						//for (int j = 0; j < lengthLongerRSBlock - numErrorCollectionCode / numRSBlocks ; j++) {
						int mod = 0;
						for (int j = 0; j < lengthLongerRSBlock; j++) {
							//System.out.println(" j = " + j);
							//try {
							if (j == lengthShorterRSBlock - eccPerRSBlock) mod = numShorterRSBlocks;

//							System.out.print("," + String.valueOf(j * numRSBlocks + i - mod));
							longerRSBlocks[i - numShorterRSBlocks][j] = blocks[j * numRSBlocks + i - mod];
							//} catch (Exception e) {e.getMessage();}
						}

						//System.out.println("debug2");
						//[TODO]エラー訂正処理
						
//						for (int k = 0; k < longerRSBlocks[i - numShorterRSBlocks].length; k++) {
//							System.out.print(String.valueOf(longerRSBlocks[i - numShorterRSBlocks][k]) + ",");
//						}
						
						//System.out.println("");
						//System.out.println("numWords="+String.valueOf(longerRSBlocks[i - numShorterRSBlocks].length));

						
						ReedSolomon corrector = new ReedSolomon(longerRSBlocks[i - numShorterRSBlocks]);
						corrector.correct();
						numErrors += corrector.getNumCorrectedErrors();


					}
				}
				int p = 0;
				for (int i = 0; i < numRSBlocks; i++) {
					if (i < numShorterRSBlocks) {
						for (int j = 0; j < lengthShorterRSBlock - eccPerRSBlock; j++) {
							dataBlocks[p++] = shorterRSBlocks[i][j];
						}
					}
					else {
						for (int j = 0; j < lengthLongerRSBlock - eccPerRSBlock; j++) {
							dataBlocks[p++] = longerRSBlocks[i - numShorterRSBlocks][j];
						}
					}
				}
			}
			if (numErrors > 0)
				canvas.println(String.valueOf(numErrors) + " data errors corrected.");
			return dataBlocks;
		}
	}
	
	String getDecodedString(int[] blocks, int version) throws IllegalDataBlockException {
		//canvas.println("Reading data.");
		String dataString = null;

		QRCodeDataBlockReader reader = new QRCodeDataBlockReader(blocks, version);
		try {
			dataString = reader.getDataString();
		} catch (ArrayIndexOutOfBoundsException e) {
			//canvas.println("ERROR: Data block error");
			throw new IllegalDataBlockException();
		}
		return dataString;
	}
	public DebugCanvas getCanvas() {
		return canvas;
	}
	public void setCanvas(DebugCanvas canvas) {
		this.canvas = canvas;
	}
}

