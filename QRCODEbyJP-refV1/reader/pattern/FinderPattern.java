package jp.sourceforge.qrcode.codec.reader.pattern;


import jp.sourceforge.qrcode.codec.reader.*;
import jp.sourceforge.qrcode.codec.exception.FinderPatternNotFoundException;
import jp.sourceforge.qrcode.codec.exception.InvalidVersionInformationException;
import jp.sourceforge.qrcode.codec.exception.UnsupportedVersionException;
import jp.sourceforge.qrcode.codec.exception.VersionInformationException;
import jp.sourceforge.qrcode.codec.geom.*;

import java.util.*;
import jp.sourceforge.qrcode.codec.util.*;

public class FinderPattern {
	public static final int UL = 0;
	public static final int UR = 1;
	public static final int DL = 2;
	static final int[] VersionInfoBit = {
			0x07C94,0x085BC,0x09A99,0x0A4D3,0x0BBF6,0x0C762,0x0D847,
			0x0E60D,0x0F928,0x10B78,0x1145D,0x12A17,0x13532,0x149A6,
			0x15683,0x168C9,0x177EC,0x18EC4,0x191E1,0x1AFAB,0x1B08E,
			0x1CC1A,0x1D33F,0x1ED75,0x1F250,0x209D5,0x216F0,0x228BA,
			0x2379F,0x24B0B,0x2542E,0x26A64,0x27541,0x28C69
	};
	
	static DebugCanvas canvas = DebugCanvas.getCanvas();
	Point[] center;
	int version;
	int[] sincos;
	int[] width;
	int moduleSize;
	
	public static FinderPattern findFinderPattern(boolean[][] image)
			throws FinderPatternNotFoundException,
			VersionInformationException {
		Line[] lineAcross = findLineAcross(image);
		Line[] lineCross = findLineCross(lineAcross);
		Point[] center = null;
		try {
			center = getCenter(lineCross);
		} catch (FinderPatternNotFoundException e) {
			throw e;
			//e.printStackTrace();
		}
//		if (center == null)
//			return new FinderPattern (center, 0, new int[2], new int[3], 0);
		int[] sincos = getAngle(center);
		center = sort(center, sincos);
		int[] width = getWidth(image, center, sincos);
		int[] moduleSize = {(width[UR] << QRCodeImageReader.DECIMAL_POINT) / 7,(width[DL] << QRCodeImageReader.DECIMAL_POINT) / 7};
		int version = calcRoughVersion(center, width);
		if (version > 6) {
			try {
				version = calcExactVersion(center, sincos, moduleSize, image);
			} catch (VersionInformationException e) {
				throw e;
			}
		}
		return new FinderPattern (center, version, sincos, width, moduleSize[0]);
	}
	
	FinderPattern (Point[] center, int version, int[] sincos, int[] width, int moduleSize) {
		this.center = center;
		this.version = version;
		this.sincos = sincos;
		this.width = width;
		this.moduleSize = moduleSize;
	}
	
	public Point[] getCenter() {
		return center;
	}
	
	public Point getCenter(int position) {
		if (position >= UL && position <= DL)
			return center[position];	
		else
			return null;
	}
	
	public int getWidth(int position) {
		return width[position];
	}
	
	public int[] getAngle() {
		return sincos;
	}
	
	public int getVersion() {
		return version;
	}
	
	public int getModuleSize() {
		return moduleSize;
	}
	public int getSqrtNumModules() {
		return 17 + 4 * version;
	}
	
	/*
	 * 位置要素検出パターンを検出するために、(暗:明:暗:明:暗)=(1:1:3:1:1)の
	 * パターンに一致する区間を線分として抽出する。
	 * 注意：位置要素パターンを横切らない部分が抽出されることもある
	 */
	static Line[] findLineAcross(boolean[][] image) {
		final int READ_HORIZONTAL = 0;
		final int READ_VERTICAL = 1;

		int imageWidth = image.length;
		int imageHeight = image[0].length;

		//int currentX = 0, currentY = 0;
		Point current = new Point();
		Vector lineAcross = new Vector();
		
		//直近の、同じ色で連続した要素の長さ
		int[] lengthBuffer = new int[5];
		int  bufferPointer = 0;
		
		int direction = READ_HORIZONTAL; //平行方向から読み始める
		boolean lastElement = QRCodeImageReader.POINT_LIGHT;
	
		while(true) {
			//要素チェック
			boolean currentElement = image[current.getX()][current.getY()];
			if (currentElement == lastElement) { //前回の要素と同じ
				lengthBuffer[bufferPointer]++;
			}
			else { //前回の要素と違う
				if (currentElement == QRCodeImageReader.POINT_LIGHT) {
					if (checkPattern(lengthBuffer, bufferPointer)) { //パターンを検出したら
						int x1, y1, x2, y2;
						if (direction == READ_HORIZONTAL) {
							//パターン候補の両端のx座標を求める
							//左端は現在の要素のx座標とバッファの長さの和から算出
							x1 = current.getX(); 
							for (int j = 0; j < 5; j++) {
								x1 -= lengthBuffer[j];
							}
							x2 = current.getX() - 1; //右端は直前の要素のx座標
							y1 = y2 = current.getY();
						}
						else {
							x1 = x2 = current.getX();
							//パターン候補の両端のy座標を求める
							//左端は現在の要素のy座標とバッファの長さの和から算出
							y1 = current.getY(); 
							for (int j = 0; j < 5; j++) {
								y1 -= lengthBuffer[j];
							}
							y2 = current.getY() - 1; //右端は直前の要素のy座標
						}
						lineAcross.addElement(new Line(x1, y1, x2, y2));
					}
				}
				bufferPointer = (bufferPointer + 1) % 5; 
				lengthBuffer[bufferPointer] = 1;
				lastElement = !lastElement;
			}
			
			//次を読む or 読み取り方向転換 or ループ終了 判定
			if (direction == READ_HORIZONTAL) { //X軸方向読み込み中
				if (current.getX() < imageWidth - 1) {
					current.translate(1, 0);
				}
				else if (current.getY() < imageHeight - 1) {
					current.set(0, current.getY() + 1);
					lengthBuffer =  new int[5];
				}
				else {
					current.set(0, 0); //読み込み点をリセットし
					lengthBuffer =  new int[5];
					direction = READ_VERTICAL; //次はY軸方向に読み始める
				}
			}
			else { //Y軸方向読み込み中
				if (current.getY() < imageHeight - 1)
					current.translate(0, 1);
				else if (current.getX() < imageWidth - 1) {
					current.set(current.getX() + 1, 0);
					lengthBuffer = new int[5];
				}
				else {
					break;
				}
			}
		}
		
		Line[] foundLines = new Line[lineAcross.size()];

		for (int i = 0; i < foundLines.length; i++)
			foundLines[i] = (Line) lineAcross.elementAt(i);
		
		canvas.drawLines(foundLines,Color.LIGHTGREEN);
		return foundLines;
	}
	
	static boolean checkPattern(int[] buffer, int pointer) {
		final int[] modelRatio = {1, 1, 3, 1, 1};	

		int baselength = 0;
		for (int i = 0; i < 5; i++) {
			baselength += buffer[i];
		}
		baselength <<= QRCodeImageReader.DECIMAL_POINT; //丸め誤差を減らすために4098倍する
		baselength /= 7;
		int i;
		for  (i = 0; i < 5; i++) {
			int leastlength = baselength * modelRatio[i] - baselength / 2;
			int mostlength = baselength * modelRatio[i] + baselength / 2;
			
			//FIXME 試験的に条件を甘くする
			leastlength -= baselength / 8;
			mostlength += baselength / 8;
			
			int targetlength = buffer[(pointer + i + 1) % 5] << QRCodeImageReader.DECIMAL_POINT;
			if (targetlength < leastlength || targetlength > mostlength) {
				return false;
			}
		}
		return true;
	}

	
	//位置要素検出パターンの中心を通り、互いに交差していると思われる線分を得る
	static Line[] findLineCross(Line[] lineAcross) {
		Vector crossLines = new Vector();
		Vector lineNeighbor = new Vector();
		Vector lineCandidate = new Vector();
		Line compareLine;
		for (int i = 0; i < lineAcross.length; i++)
			lineCandidate.addElement(lineAcross[i]);
		
		for (int i = 0; i < lineCandidate.size() - 1; i++) {
			lineNeighbor.removeAllElements();
			lineNeighbor.addElement(lineCandidate.elementAt(i));
			for (int j = i + 1; j < lineCandidate.size(); j++) {
				if (Line.isNeighbor((Line)lineNeighbor.lastElement(), (Line)lineCandidate.elementAt(j))) {
					lineNeighbor.addElement(lineCandidate.elementAt(j));
					compareLine = (Line)lineNeighbor.lastElement();
					if (lineNeighbor.size() * 5 > compareLine.getLength() &&
							j == lineCandidate.size() - 1) {
						crossLines.addElement(lineNeighbor.elementAt(lineNeighbor.size() / 2));
						for (int k = 0; k < lineNeighbor.size(); k++)
							lineCandidate.removeElement(lineNeighbor.elementAt(k));
					}
				}
				//これ以上調べても隣り合うLineが見つかる可能性がない場合は比較打ち切り
				else if (cantNeighbor((Line)lineNeighbor.lastElement(), (Line)lineCandidate.elementAt(j)) ||
					(j == lineCandidate.size() - 1)) {
					compareLine = (Line)lineNeighbor.lastElement();
					//隣り合った線分同士の幅がそれを構成する線分の長さの
					//1/6以上だったら、位置要素検出パターンを横切っていると判断する					
					if (lineNeighbor.size() * 6 > compareLine.getLength()) {
						crossLines.addElement(lineNeighbor.elementAt(lineNeighbor.size() / 2));
						for (int k = 0; k < lineNeighbor.size(); k++) {
							lineCandidate.removeElement(lineNeighbor.elementAt(k));
						}
					}
					break;
				}
			}
		}	

		Line[] foundLines = new Line[crossLines.size()];
		for (int i = 0; i < foundLines.length; i++) {
			foundLines[i] = (Line) crossLines.elementAt(i);
		}
		return foundLines;
	}
	
	static boolean cantNeighbor(Line line1, Line line2) {
		if (Line.isCross(line1, line2))
			return true;

		if (line1.isHorizontal()) {
			if (Math.abs(line1.getP1().getY() - line2.getP1().getY()) > 1)
				return true;
			else
				return false;
		} 
		else {
			if (Math.abs(line1.getP1().getX() - line2.getP1().getX()) > 1)
				return true;
			else
				return false;
			
		}
	}
	
	//シンボルの傾斜角度を求める
	static int[] getAngle(Point[] centers) {

		Line[] additionalLine = new Line[3];

		for (int i = 0; i < additionalLine.length; i++) {
			additionalLine[i] = new Line(centers[i],
					centers[(i + 1) % additionalLine.length]);
		}
		Line longestLine = Line.getLongest(additionalLine);
		Point originPoint = new Point();
		for (int i = 0; i < centers.length; i++) {
			if ((centers[i].getX() != longestLine.getP1().getX() ||
					centers[i].getY() != longestLine.getP1().getY()) &
					(centers[i].getX() != longestLine.getP2().getX() ||
					centers[i].getY() != longestLine.getP2().getY())) {
				originPoint = centers[i];
				break;
			}
		}
		Point remotePoint = new Point();


		//左上のパターン中心点を原点として、他の2パターンの中心点がどこにあるか
		//および、角度計測の対象となる点はそれらのうちどちらか
		if (originPoint.getY() <= longestLine.getP1().getY() & //第1または第2象限
				originPoint.getY() <= longestLine.getP2().getY())
			if (longestLine.getP1().getX() < longestLine.getP2().getX())
				remotePoint = longestLine.getP2();
			else
				remotePoint = longestLine.getP1();
		else if (originPoint.getX() >= longestLine.getP1().getX() & //第2または第3象限
				originPoint.getX() >= longestLine.getP2().getX())
			if (longestLine.getP1().getY() < longestLine.getP2().getY())
				remotePoint = longestLine.getP2();
			else
				remotePoint = longestLine.getP1();
		else if (originPoint.getY() >= longestLine.getP1().getY() & //第3または第4象限
				originPoint.getY() >= longestLine.getP2().getY())
			if (longestLine.getP1().getX() < longestLine.getP2().getX())
				remotePoint = longestLine.getP1();
			else
				remotePoint = longestLine.getP2();
		else //第1または第4象限
			if (longestLine.getP1().getY() < longestLine.getP2().getY())
				remotePoint = longestLine.getP1();
			else
				remotePoint = longestLine.getP2();
		
		int r = new Line(originPoint, remotePoint).getLength();
		int sincos[] = new int[2];
		sincos[0] = ((remotePoint.getY() - originPoint.getY()) << QRCodeImageReader.DECIMAL_POINT) / r; //Sin
		sincos[1] = ((remotePoint.getX() - originPoint.getX()) << QRCodeImageReader.DECIMAL_POINT) / r; //Cos
//		sincos[0] = (sincos[0] == 0) ? 1 : sincos[0];
//		sincos[1] = (sincos[1] == 0) ? 1 : sincos[1];
		return sincos;
	}
	

	
	
	static Point[] getCenter(Line[] crossLines) 
			throws FinderPatternNotFoundException {
		Vector centers = new Vector();
		for (int i = 0; i < crossLines.length - 1; i++) {
			Line compareLine = crossLines[i];
			for (int j = i + 1; j < crossLines.length; j++) {
				Line comparedLine = crossLines[j];
				if (Line.isCross(compareLine, comparedLine)) {
					int x = 0;
					int y = 0;
					if (compareLine.isHorizontal()) {
						x = compareLine.getCenter().getX();
						y = comparedLine.getCenter().getY();		
					}
					else {
						x = comparedLine.getCenter().getX();
						y = compareLine.getCenter().getY();
					}
					centers.addElement(new Point(x,y));
				}
			}
		}
		
		Point[] foundPoints = new Point[centers.size()];
		
		for (int i = 0; i < foundPoints.length; i++) {
			foundPoints[i] = (Point)centers.elementAt(i);
			System.out.println(foundPoints[i]);
		}
		System.out.println(foundPoints.length);
		
		if (foundPoints.length == 3) {
			canvas.drawPolygon(foundPoints, Color.LIGHTRED);	
			return foundPoints;
		}
		else
			throw new FinderPatternNotFoundException();
	}

	
	//各位置要素検出パターンの中心点を格納した配列を、
	//左上 points[0] 右上 points[1] 左下 points[2]になるようにソートする
	static Point[] sort(Point[] centers, int[] sincos) {

		Point[] sortedCenters = new Point[3];
		
		int quadant = getURQuadant(sincos);
		switch (quadant) {
		case 1:
			sortedCenters[1] = getPointAtSide(centers, Point.RIGHT, Point.BOTTOM);
			sortedCenters[2] = getPointAtSide(centers, Point.BOTTOM, Point.LEFT);
			break;
		case 2:
			sortedCenters[1] = getPointAtSide(centers, Point.BOTTOM, Point.LEFT);
			sortedCenters[2] = getPointAtSide(centers, Point.TOP, Point.LEFT);
			break;
		case 3:
			sortedCenters[1] = getPointAtSide(centers, Point.LEFT, Point.TOP);			
			sortedCenters[2] = getPointAtSide(centers, Point.RIGHT, Point.TOP);
			break;
		case 4:
			sortedCenters[1] = getPointAtSide(centers, Point.TOP, Point.RIGHT);
			sortedCenters[2] = getPointAtSide(centers, Point.BOTTOM, Point.RIGHT);
			break;
		}

		//最後に左上のパターンを調べる
		for (int i = 0; i < centers.length; i++) {
			if (!centers[i].equals(sortedCenters[1]) && 
					!centers[i].equals(sortedCenters[2])) {
				sortedCenters[0] = centers[i];
			}
		}

		return sortedCenters;
	}
	
	static int getURQuadant(int[] sincos) {
		int sin = sincos[0];
		int cos = sincos[1];
		if (sin >= 0 && cos > 0)
			return 1;
		else if (sin > 0 && cos <= 0)
			return 2;
		else if (sin <= 0 && cos < 0)
			return 3;
		else if (sin < 0 && cos >= 0)
			return 4;
		
		return 0;
	}
	
	static Point getPointAtSide(Point[] points, int side1, int side2) {
		Point sidePoint = new Point();
		int x = ((side1 == Point.RIGHT || side2 == Point.RIGHT) ? 0 : Integer.MAX_VALUE);
		int y = ((side1 == Point.BOTTOM || side2 == Point.BOTTOM) ? 0 : Integer.MAX_VALUE);
		sidePoint = new Point(x, y);
			
		for (int i = 0; i < points.length; i++) {
			switch (side1) {
			case Point.RIGHT:
				if (sidePoint.getX() < points[i].getX()) {
					sidePoint = points[i];
				}
				else if (sidePoint.getX() == points[i].getX()) {
					if (side2 == Point.BOTTOM) {
						if (sidePoint.getY() < points[i].getY()) {
							sidePoint = points[i];
						}
					}
					else {
						if (sidePoint.getY() > points[i].getY()) {
							sidePoint = points[i];
						}
					}
				}
				break;
			case Point.BOTTOM:
				if (sidePoint.getY() < points[i].getY()) {
					sidePoint = points[i];
				}
				else if (sidePoint.getY() == points[i].getY()) {
					if (side2 == Point.RIGHT) {
						if (sidePoint.getX() < points[i].getX()) {
							sidePoint = points[i];
						}
					}
					else {
						if (sidePoint.getX() > points[i].getX()) {
							sidePoint = points[i];
						}
					}
				}
				break;
			case Point.LEFT:
				if (sidePoint.getX() > points[i].getX()) {
					sidePoint = points[i];
				}
				else if (sidePoint.getX() == points[i].getX()) {
					if (side2 == Point.BOTTOM) {
						if (sidePoint.getY() < points[i].getY()) {
							sidePoint = points[i];
						}
					}
					else {
						if (sidePoint.getY() > points[i].getY()) {
							sidePoint = points[i];
						}
					}
				}
				break;
			case Point.TOP:
				if (sidePoint.getY() > points[i].getY()) {
					sidePoint = points[i];
				}
				else if (sidePoint.getY() == points[i].getY()) {
					if (side2 == Point.RIGHT) {
						if (sidePoint.getX() < points[i].getX()) {
							sidePoint = points[i];
						}
					}
					else {
						if (sidePoint.getX() > points[i].getX()) {
							sidePoint = points[i];
						}
					}
				}
				break;
			}
		}
		return sidePoint;
	}
	
	static int[] getWidth(boolean[][] image, Point[] centers,  int[] sincos) {

		int[] width = new int[3];
		
		for (int i = 0; i < 3; i++) {
			boolean flag = false;
			int lx, rx;
			int y = centers[i].getY();
			for (lx = centers[i].getX(); lx >= 0; lx--) {
				if (image[lx][y] == QRCodeImageReader.POINT_DARK &&
						image [lx - 1][y] == QRCodeImageReader.POINT_LIGHT) {
					if (flag == false) flag = true;
					else break;
				}
			}
			flag = false;
			for (rx = centers[i].getX(); rx < image.length; rx++) {
				if (image[rx][y] == QRCodeImageReader.POINT_DARK &&
						image[rx + 1][y] == QRCodeImageReader.POINT_LIGHT)  {
					if (flag == false) flag = true;
					else break;
				}
			}
			width[i] = (rx - lx + 1);
		}
		return width;
	}
	
	//型番を仮に求める
	static int calcRoughVersion(Point[] center, int[] width) {
		final int dp = QRCodeImageReader.DECIMAL_POINT;
		int lengthAdditionalLine = (new Line(center[UL], center[UR]).getLength()) << dp ;
		//System.out.println("lengthAdditionalLine" + Integer.toString(lengthAdditionalLine));
		int avarageWidth = ((width[UL] + width[UR]) << dp) / 14;
		//System.out.println("avarageWidth:" + Integer.toBinaryString(avarageWidth));
		int roughVersion = ((lengthAdditionalLine / avarageWidth) - 10) / 4;
		if (((lengthAdditionalLine / avarageWidth) - 10) % 4 >= 2) {
			roughVersion++;
		}
		//if (tempVersion % 4096 < 2048)
			//tempVersion >>= QRCodeImageReader.DECIMAL_POINT;
		//else {
		//	tempVersion >>= QRCodeImageReader.DECIMAL_POINT;
		//	tempVersion++; //桁落ち対策
		//}
		//if (tempVersion <= 6)
		return roughVersion;

		
	}
	
	static int calcExactVersion(Point[] centers, int[] sincos, int[] moduleSize, boolean[][] image) 
	throws InvalidVersionInformationException,
					UnsupportedVersionException {
		boolean[] versionInformation = new boolean[18];
		//int offsetX = -(moduleSize * 8) >> 12;
		//int offsetY = -(moduleSize * 3) >> 12;
		Point[] points = new Point[18];
		int targetX, targetY;
		int sin = sincos[0];
		int cos = sincos[1];
		Axis axis = new Axis(sin, cos, moduleSize[0]); //UR
		axis.setOrigin(centers[UR]);

		for (int y = 0; y < 6; y++) {
			for (int x = 0; x < 3; x++) {

				//targetX = finderX + ((moduleSize * (x-7)) >> QRCodeImageReader.DECIMAL_POINT);
				targetX = axis.translate(x - 7, 0).getX();
				//targetY = finderY + ((moduleSize * (y-3)) >> QRCodeImageReader.DECIMAL_POINT);
				targetY = axis.translate(0, y - 3).getY();
				versionInformation[x + y * 3] = image[targetX][targetY];
				points[x + y * 3] = new Point(targetX, targetY);
				//System.out.println(points[x + y * 3]);
				//points[x + y * 3] = axis.translate(x - 7, y - 3);
			}
			//System.out.println("");
		}
		//System.out.println("");
		canvas.drawPoints(points, Color.LIGHTRED);
		
		int exactVersion = 0;
		try {
			exactVersion = checkVersionInfo(versionInformation);
		} catch (VersionInformationException e) {
			canvas.println("Version info error. now retry with other place one.");
			axis.setOrigin(centers[DL]);
			axis.setModulePitch(moduleSize[1]); //DL
			
			for (int x = 0; x < 6; x++) {
				for (int y = 0; y < 3; y++) {
					targetX = axis.translate(x - 3, 0).getX();
					targetY = axis.translate(0, y - 7).getY();
					versionInformation[y + x * 3] = image[targetX][targetY];
					points[x + y * 3] = new Point(targetX, targetY);
				}
			}
			canvas.drawPoints(points, Color.LIGHTRED);
			
			try {
				exactVersion = checkVersionInfo(versionInformation);
			} catch (VersionInformationException e2) {
				e.printStackTrace();
				throw e2;
			}
		}
		return exactVersion;
	}
	
	static int checkVersionInfo(boolean[] target)
			throws InvalidVersionInformationException{
		int errorCount = 0, versionBase;
		for (versionBase = 0; versionBase < VersionInfoBit.length; versionBase++) {
			errorCount = 0;
			for (int j = 0; j < 18; j++) {
				if (target[j] ^ (VersionInfoBit[versionBase] >> j) % 2 == 1)
					errorCount++;
			}
			if (errorCount <= 3) break;
		}
		if (errorCount <= 3)
			return 7 + versionBase;
		else
			throw new InvalidVersionInformationException();
	}


}
