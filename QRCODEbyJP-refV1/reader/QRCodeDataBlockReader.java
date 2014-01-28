/*
 * 作成日： 2004/10/04
 *
 * TODO この生成されたファイルのテンプレートを変更するには次を参照。
 * ウィンドウ ＞ 設定 ＞ Java ＞ コード・スタイル ＞ コード・テンプレート
 */
package jp.sourceforge.qrcode.codec.reader;

import jp.sourceforge.qrcode.codec.util.DebugCanvas;
/**
 * @author Owner
 *
 * TODO この生成された型コメントのテンプレートを変更するには次を参照。
 * ウィンドウ ＞ 設定 ＞ Java ＞ コード・スタイル ＞ コード・テンプレート
 */
public class QRCodeDataBlockReader {
	int[] blocks;
	int version;
	int mode = -1;
	int blockPointer;
	int bitPointer;
	int dataLength;
	DebugCanvas canvas;
	final int MODE_NUMBER = 1;
	final int MODE_ROMAN_AND_NUMBER = 2;
	final int MODE_8BIT_BYTE = 4;
	final int MODE_KANJI = 8;

	public QRCodeDataBlockReader(int[] blocks, int version) {
		blockPointer = 0;
		bitPointer = 7;
		dataLength = 0;
		this.blocks = blocks;
		this.version = version;
		canvas = DebugCanvas.getCanvas();
	}
	
	int getNextBits(int numBits) throws ArrayIndexOutOfBoundsException {
//		System.out.println("numBits:" + String.valueOf(numBits));
//		System.out.println("blockPointer:" + String.valueOf(blockPointer));
//		System.out.println("bitPointer:" + String.valueOf(bitPointer));
		if (numBits < bitPointer + 1) { // 1つのブロックで収まる
			int mask = 0;
			for (int i = 0; i < numBits; i++) {
				mask += 1 << i;
			}
			mask <<= (bitPointer - numBits + 1);
			
			int bits = (blocks[blockPointer] & mask) >> (bitPointer - numBits + 1);
			bitPointer -= numBits;
			return bits;
		}
		else if (numBits < bitPointer + 1 + 8) { //2つのブロックにまたがる
			int mask1 = 0;
			for (int i = 0; i < bitPointer + 1; i++) {
				mask1 += 1 << i;
			}
			int bits = (blocks[blockPointer] & mask1) << (numBits - (bitPointer + 1));
			blockPointer++;

			bits += (blocks[blockPointer]) >> (8 - (numBits - (bitPointer + 1)));

	
			bitPointer = bitPointer - numBits % 8;
			if (bitPointer < 0) {
				bitPointer = 8 + bitPointer;
			}
			return bits;	
		}
		else if (numBits < bitPointer + 1 + 16) { //3つのブロックにまたがる
			int mask1 = 0; //第1ブロックのマスク
			int mask3 = 0; //第3ブロックのマスク
			//bitPointer + 1 : 第1ブロックのビット数
			//8 : 第2ブロックのビット数(常に8)
			//numBits - (bitPointer + 1 + 8) : 第3ブロックのビット数
			for (int i = 0; i < bitPointer + 1; i++) {
				mask1 += 1 << i;
			}
			int bitsFirstBlock = (blocks[blockPointer] & mask1) << (numBits - (bitPointer + 1));
			blockPointer++;

			int bitsSecondBlock = blocks[blockPointer] << (numBits - (bitPointer + 1 + 8));
			blockPointer++;
			
			for (int i = 0; i < numBits - (bitPointer + 1 + 8); i++) {
				mask3 += 1 << i;
			}
			mask3 <<= 8 - (numBits - (bitPointer + 1 + 8));
			int bitsThirdBlock = (blocks[blockPointer] & mask3) >> (8 - (numBits - (bitPointer + 1 + 8)));
			
			int bits = bitsFirstBlock + bitsSecondBlock + bitsThirdBlock;
			bitPointer = bitPointer - (numBits - 8) % 8;
			if (bitPointer < 0) {
				bitPointer = 8 + bitPointer;
			}
			return bits;
		}
		else {
			System.out.println("ERROR!");
			return 0;
		}
	}	
	
	int getNextMode() throws ArrayIndexOutOfBoundsException {
		return getNextBits(4);
	}
	
	int guessMode(int mode) {
		//0001 0010 0011 0100
		//0101 0110 0111 1000
		
		if (mode == 3)
			return MODE_ROMAN_AND_NUMBER;
//		else if (mode == 5 || mode == 6)  
//			return MODE_8BIT_BYTE;
		else // mode > 8
			return MODE_KANJI;
	}

	int getDataLength(int mode) throws ArrayIndexOutOfBoundsException {
		switch (mode) {
		case MODE_NUMBER:
			if (version <= 9)
				return getNextBits(10);
			else if (version >= 10 && version <= 26)
				return getNextBits(12);
		case MODE_ROMAN_AND_NUMBER:
			if (version <= 9)
				return getNextBits(9);
			else if (version >= 10 && version <= 26)
				return getNextBits(11);
		case MODE_8BIT_BYTE:
			if (version <= 9)
				return getNextBits(8);
			else if (version >= 10 && version <= 26)
				return getNextBits(16);
		case MODE_KANJI:
			if (version <= 9)
				return getNextBits(8);
			else if (version >= 10 && version <= 26)
				return getNextBits(10);
		default:
				return 0;
		}
	}	
	
	public String getDataString() throws ArrayIndexOutOfBoundsException {
		canvas.println("Reading data blocks.");
		String dataString = "";
		do {
			mode = getNextMode();
			//System.out.println("mode: " + mode);
			if (mode == 0)
				break;
			//if (mode != 1 && mode != 2 && mode != 4 && mode != 8)
			//	break;
			//}
			if (mode != MODE_NUMBER && mode != MODE_ROMAN_AND_NUMBER &&
					mode != MODE_8BIT_BYTE && mode != MODE_KANJI) {
				mode = guessMode(mode);
				//System.out.println("guessed mode: " + mode);

			}
				
			dataLength = getDataLength(mode);
			//System.out.println("length: " + dataLength);
			switch (mode) {
			case MODE_NUMBER: //数字モード
				//canvas.println("Mode: Figure");
				dataString += getFigureString(dataLength);
				break;
			case MODE_ROMAN_AND_NUMBER: //英数字モード
				//canvas.println("Mode: Roman&Figure");
				dataString += getRomanAndFigureString(dataLength);
				break;
			case MODE_8BIT_BYTE: //8ビットバイトモード
				//canvas.println("Mode: 8bit Byte");
				dataString += get8bitByteString(dataLength);
				break;
			case MODE_KANJI: //漢字モード
				//canvas.println("Mode: Kanji");
				dataString += getKanjiString(dataLength);
				break;
			}
			//canvas.println("DataLength: " + dataLength);
			//System.out.println(dataString);
		} while (true);
		System.out.println("");
		return dataString;
	}
	
	
	String getFigureString(int dataLength) throws ArrayIndexOutOfBoundsException {
		int length = dataLength;
		int intData = 0;
		String strData = "";
		do {
			if (length >= 3) {
				intData = getNextBits(10);
				if (intData < 100) strData += "0";
				if (intData < 10) strData += "0";
				length -= 3;
			}
			else if (length == 2) {
				intData = getNextBits(7);
				if (intData < 10) strData += "0";
				length -= 2;
			}
			else if (length == 1) {
				intData = getNextBits(4);
				length -= 1;
			}				
			strData += Integer.toString(intData);
		} while (length > 0);
		
		return strData;
	}
	
	String getRomanAndFigureString(int dataLength) throws ArrayIndexOutOfBoundsException  {
		int length = dataLength;
		int intData = 0;
		String strData = "";
		final char[] tableRomanAndFigure = {
			 '0', '1', '2', '3', '4', '5',
	 		 '6', '7', '8', '9', 'A', 'B',
			 'C', 'D', 'E', 'F', 'G', 'H',
			 'I', 'J', 'K', 'L', 'M', 'N',
			 'O', 'P', 'Q', 'R', 'S', 'T',
			 'U', 'V', 'W', 'X', 'Y', 'Z',
			 ' ', '$', '%', '*', '+', '-',
			 '.', '/', ':'
			 };
		do {
			if (length > 1) {
				intData = getNextBits(11);
				int firstLetter = intData / 45;
				int secondLetter = intData % 45;
				strData += String.valueOf(tableRomanAndFigure[firstLetter]);
				strData += String.valueOf(tableRomanAndFigure[secondLetter]);
				length -= 2;
			}
			else if (length == 1) {
				intData = getNextBits(6);
				strData += String.valueOf(tableRomanAndFigure[intData]);
				length -= 1;
			}
		} while (length > 0);
		
		return strData;
	}
	
	String get8bitByteString(int dataLength) throws ArrayIndexOutOfBoundsException  {
		int length = dataLength;
		int intData = 0;
		String strData = "";
		final char[] table8bitByte = {
			' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', '\n', ' ', ' ', ' ', ' ', ' ', 
			' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', 
			' ', '!', '"', '#', '$', '%', '&', '\'', '(', ')', '*', '+', ',', '-', '.', '/',  
			'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', ':', ';', '<', '=', '>', '?', 
			'@', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 
			'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '[', '\\', ']', '^', '_', 
			'`', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 
			'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '{', '|', '}', '~', ' ', 
			' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', 
			' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', 
			' ', '｡', '｢', '｣', '､', '･', 'ｦ', 'ｧ', 'ｨ', 'ｩ', 'ｪ', 'ｫ', 'ｬ', 'ｭ', 'ｮ', 'ｯ', 
			'-', 'ｱ', 'ｲ', 'ｳ', 'ｴ', 'ｵ', 'ｶ', 'ｷ', 'ｸ', 'ｹ', 'ｺ', 'ｻ', 'ｼ', 'ｽ', 'ｾ', 'ｿ', 
			'ﾀ', 'ﾁ', 'ﾂ', 'ﾃ', 'ﾄ', 'ﾅ', 'ﾆ', 'ﾇ', 'ﾈ', 'ﾉ', 'ﾊ', 'ﾋ', 'ﾌ', 'ﾍ', 'ﾎ', 'ﾏ', 
			'ﾐ', 'ﾑ', 'ﾒ', 'ﾓ', 'ﾔ', 'ﾕ', 'ﾖ', 'ﾗ', 'ﾘ', 'ﾙ', 'ﾚ', 'ﾛ', 'ﾜ', 'ﾝ', 'ﾞ', 'ﾟ', 
			' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ',
			' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '
			};
		do {
			intData = getNextBits(8);
			strData += String.valueOf(table8bitByte[intData]);
			length--;
		} while (length > 0);
		return strData;
	}

	String getKanjiString(int dataLength) throws ArrayIndexOutOfBoundsException {
		int length = dataLength;
		int intData = 0;
		String unicodeString = "";
		do {
			intData = getNextBits(13);
			int lowerByte = intData % 0xC0;
			int higherByte = intData / 0xC0;

			int tempWord = (higherByte << 8) + lowerByte;
			int shiftjisWord = 0;
			if (tempWord + 0x8140 <= 0x9FFC) { //8140 ～ 9FFC の間
				shiftjisWord = tempWord + 0x8140;
			}
			else { // E040 ～ EBBF の間
				shiftjisWord = tempWord + 0xC140;
			}

			byte[] tempByte = new byte[2];
			tempByte[0] = (byte)(shiftjisWord >> 8);
			tempByte[1] = (byte)(shiftjisWord & 0xFF);
			unicodeString += new String(tempByte);
			length--;
		} while (length > 0);

			
		return unicodeString;
	}
	
}
