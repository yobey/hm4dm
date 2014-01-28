package jp.sourceforge.qrcode.codec.reader;


import jp.sourceforge.qrcode.codec.data.*;
import jp.sourceforge.qrcode.codec.exception.AlignmentPatternEdgeNotFoundException;
import jp.sourceforge.qrcode.codec.exception.FinderPatternNotFoundException;
import jp.sourceforge.qrcode.codec.exception.VersionInformationException;
import jp.sourceforge.qrcode.codec.geom.*;
import jp.sourceforge.qrcode.codec.reader.pattern.*;
import jp.sourceforge.qrcode.codec.util.*;

public class QRCodeImageReader {

	DebugCanvas canvas;
	//boolean[][] image;
	//DP = 
	//23 ... max 255
	//22 .. max 511
	//21 .. max 1023
	public static  int DECIMAL_POINT = 23;
	public static final boolean POINT_DARK = true;
	public static final boolean POINT_LIGHT = false;

	//int numModuleAtSide; //デコード対象のシンボルにおける一辺のモジュールの数

	public QRCodeImageReader() {
		//this.image = image;
		this.canvas = DebugCanvas.getCanvas();
	}
	
	
	boolean[][] applyMedianFilter(boolean[][] image, int threshold) {
		boolean[][] filteredMatrix = new boolean[image.length][image[0].length];
		//ノイズフィルタ(メディアンフィルタ)
		int numPointDark;
		for (int y = 1; y < image[0].length - 1; y++) {
			for (int x = 1; x < image.length - 1; x++) {
			//if (image[x][y] == true) {
			numPointDark = 0;
			for (int fy = -1; fy < 2; fy++) {
				for (int fx = -1; fx < 2; fx++) {
					if (image[x + fx][y + fy] == true) {
						numPointDark++;
					}
				}
			}
			if (numPointDark > threshold) 
				filteredMatrix[x][y] = POINT_DARK;
			}
		}
		
		return filteredMatrix;
	}
	boolean[][] applyMedianFilter_(boolean[][] image, int threshold, int internalScale) {
		boolean[][] filteredMatrix = new boolean[image.length][image[0].length];
		//ノイズフィルタ(メディアンフィルタ)
		int numPointDark;
		for (int y = internalScale; y < image[0].length - internalScale; y+=internalScale) {
			for (int x = internalScale; x < image.length - internalScale; x+=internalScale) {
				//if (image[x][y] == true) {
					numPointDark = 0;
					for (int fy = -internalScale; fy < internalScale + 1; fy+=internalScale) {
						for (int fx = -internalScale; fx < internalScale + 1; fx+=internalScale) {
							if (image[x + fx][y + fy] == true) {
								numPointDark++;
							}
						}
					}
					if (numPointDark > threshold) {
						for (int dy = 0; dy < internalScale; dy++)
							for (int dx = 0; dx < internalScale; dx++)
						filteredMatrix[x + dx][y + dy] = POINT_DARK;
					}
				//}
			}
		}
		return filteredMatrix;
	}
	
	public QRCodeSymbol getQRCodeSymbol(boolean[][] image, int internalScale) 
			throws FinderPatternNotFoundException ,
			VersionInformationException ,
			AlignmentPatternEdgeNotFoundException {
		int longSide = (image.length < image[0].length) ? image[0].length : image.length;
		QRCodeImageReader.DECIMAL_POINT = 23 - QRCodeUtility.sqrt(longSide / 256);
			
		canvas.println("Drawing matrix.");
		canvas.drawMatrix(image);
		//for(int i = 0; i < 500000; i++) System.out.println("");

		canvas.println("Scanning Finder Pattern.");
		FinderPattern finderPattern = null;
		try {
			finderPattern = FinderPattern.findFinderPattern(image);
		} catch (FinderPatternNotFoundException e) {
			canvas.println("Not found, now retrying...");
			image = applyMedianFilter(image, 5);
			canvas.drawMatrix(image);
			try {
				finderPattern = FinderPattern.findFinderPattern(image);
			} catch (FinderPatternNotFoundException e2) {
				e2.printStackTrace();
				throw e2;
			} catch (VersionInformationException e3) {
				e3.printStackTrace();
				throw e3;
			}
			
//			if (finderPattern.getCenter() == null) {
//				canvas.println("ERROR: Finder pattern not found");				
//			}
		} catch (VersionInformationException e4) {
			e4.printStackTrace();
			throw e4;
		}


		canvas.println("FinderPattern at");
		String finderPatternCoordinates = 
			finderPattern.getCenter(FinderPattern.UL).toString() + 
			finderPattern.getCenter(FinderPattern.UR).toString() +
			finderPattern.getCenter(FinderPattern.DL).toString();
		canvas.println(finderPatternCoordinates);
 		int[] sincos = finderPattern.getAngle();
		canvas.println("Angle*4098: Sin " + Integer.toString(sincos[0]) + "  " + "Cos " + Integer.toString(sincos[1]));

		Line[][][][] samplingGrid = new Line[1][1][1][1];
		int version = finderPattern.getVersion();

		canvas.println("Version: " + Integer.toString(version));

		AlignmentPattern alignmentPattern = null;
		if (version > 1) {
			try {
				alignmentPattern = AlignmentPattern.findAlignmentPattern(image, finderPattern);
			} catch (AlignmentPatternEdgeNotFoundException e) {
				e.printStackTrace();
				throw e;
			}
			
			int matrixLength = alignmentPattern.getCenter().length;
			canvas.println("AlignmentPatterns at");
			for (int y = 0; y < matrixLength; y++) {
				String alignmentPatternCoordinates = "";
				for (int x = 0; x < matrixLength; x++) {
					alignmentPatternCoordinates += alignmentPattern.getCenter()[x][y].toString();
				}
				canvas.println(alignmentPatternCoordinates);
			}
		}
		//for(int i = 0; i < 500000; i++) System.out.println("");

		canvas.println("Creating sampling grid.");
		//[TODO] need all-purpose method
		if (version == 1)
			samplingGrid = getSamplingGrid1(finderPattern);
		else if (version >= 2 && version <= 6)
			samplingGrid = getSamplingGrid2_6(finderPattern, alignmentPattern);
		else if (version >= 7 && version <= 13)
			samplingGrid = getSamplingGrid7_13(finderPattern, alignmentPattern);
//		else if (version >= 14 && version <= 20)
//			samplingGrid = getSamplingGrid14_20(finderPattern, alignmentPattern);			
		canvas.println("Reading grid.");
		boolean[][] qRCodeMatrix = getQRCodeMatrix(image, samplingGrid);
		//canvas.drawMatrix(qRCodeMatrix, 5);
		
		return new QRCodeSymbol(qRCodeMatrix);
	}
	
	//位置合せパターンがない型番1専用
	Line[][][][] getSamplingGrid1(FinderPattern finderPattern) {
		int sqrtNumArea = 1;
		int sqrtNumModules = finderPattern.getSqrtNumModules(); //一辺当たりのモジュール数を得る
		int sqrtNumAreaModules = sqrtNumModules / sqrtNumArea;
		Point[] centers = finderPattern.getCenter();
		int logicalDistance = 14;
		Line[][][][] samplingGrid = new Line[sqrtNumArea][sqrtNumArea][2][sqrtNumAreaModules];
		Line baseLineX, baseLineY, gridLineX, gridLineY;

		//モジュールピッチを得る
		int[] modulePitch = new int[2]; //up left の順に格納
		modulePitch[0] = getAreaModulePitch(centers[0], centers[1], logicalDistance);
		modulePitch[1] = getAreaModulePitch(centers[0], centers[2], logicalDistance);

		//X軸に垂直の基線(一般に縦)
		baseLineX = new Line(
				finderPattern.getCenter(FinderPattern.UL), 
				finderPattern.getCenter(FinderPattern.DL));

		int sin = finderPattern.getAngle()[0];
		int cos = finderPattern.getAngle()[1];

		Axis axis = new Axis(sin, cos, modulePitch[0]);
		axis.setOrigin(baseLineX.getP1());
		baseLineX.setP1(axis.translate(-3, -3));


		axis.setModulePitch(modulePitch[1]);
		axis.setOrigin(baseLineX.getP2());
		baseLineX.setP2(axis.translate(-3, 3));
						

		
		//Y軸に垂直の基線(一般に横)
	  baseLineY =
			new Line(finderPattern.getCenter(FinderPattern.UL),
					finderPattern.getCenter(FinderPattern.UR));
	  
		axis.setModulePitch(modulePitch[1]);
		axis.setOrigin(baseLineY.getP1());
		baseLineY.setP1(axis.translate(-3, -3));


		axis.setModulePitch(modulePitch[1]);
		axis.setOrigin(baseLineY.getP2());
		baseLineY.setP2(axis.translate(3, -3));
		
		baseLineX.translate(1,1);
		baseLineY.translate(1,1);

		for (int i = 0; i < sqrtNumAreaModules; i++) {
			
			gridLineX = new Line(baseLineX.getP1(), baseLineX.getP2());
			
			axis.setOrigin(gridLineX.getP1());
			axis.setModulePitch(modulePitch[0]);
			gridLineX.setP1(axis.translate(i,0));

			axis.setOrigin(gridLineX.getP2());
			axis.setModulePitch(modulePitch[0]);
			gridLineX.setP2(axis.translate(i,0));
			

			gridLineY = new Line(baseLineY.getP1(), baseLineY.getP2());
			axis.setOrigin(gridLineY.getP1());
			axis.setModulePitch(modulePitch[1]);
			gridLineY.setP1(axis.translate(0,i));

			axis.setOrigin(gridLineY.getP2());
			axis.setModulePitch(modulePitch[1]);
			gridLineY.setP2(axis.translate(0,i));


			samplingGrid[0][0][0][i] = gridLineX;
			samplingGrid[0][0][1][i] = gridLineY;
		}
		for (int ay = 0; ay < samplingGrid[0].length; ay++) {
			for (int ax = 0; ax < samplingGrid.length; ax++) {
				canvas.drawLines(samplingGrid[ax][ay][0], Color.LIGHTBLUE);
				canvas.drawLines(samplingGrid[ax][ay][1], Color.LIGHTBLUE);
			}
		}
		return samplingGrid;
	}
	
	//sampllingGrid[areaX][areaY][direction(x=0,y=1)][EachLines]	
	//型番2 ～ 6のサンプリンググリッドを求める
	Line[][][][] getSamplingGrid2_6(FinderPattern finderPattern, AlignmentPattern alignmentPattern) {

		Point centers[][] = alignmentPattern.getCenter();
		centers[0][0] = finderPattern.getCenter(FinderPattern.UL);
		centers[1][0] = finderPattern.getCenter(FinderPattern.UR);
		centers[0][1] = finderPattern.getCenter(FinderPattern.DL);
		int sqrtNumModules = finderPattern.getSqrtNumModules(); //一辺当たりのモジュール数を得る

		Line[][][][] samplingGrid = new Line[1][1][2][sqrtNumModules];
		Line baseLineX, baseLineY, gridLineX, gridLineY;
		
		int logicalDistance = alignmentPattern.getLogicalDistance();
		int sin = finderPattern.getAngle()[0];
		int cos = finderPattern.getAngle()[1];
		Axis axis = new Axis(sin, cos, finderPattern.getModuleSize());
	
		int[] modulePitch = new int[4]; //top left bottom rightの順に格納

		modulePitch[0] = getAreaModulePitch(centers[0][0], centers[1][0], logicalDistance + 6);
		modulePitch[1] = getAreaModulePitch(centers[0][0], centers[0][1], logicalDistance + 6);
		axis.setModulePitch(modulePitch[0]);
		axis.setOrigin(centers[0][1]);
		modulePitch[2] = getAreaModulePitch(axis.translate(0, -3), centers[1][1], logicalDistance + 3);
		axis.setModulePitch(modulePitch[1]);
		axis.setOrigin(centers[1][0]);
		modulePitch[3] = getAreaModulePitch(axis.translate(-3, 0), centers[1][1], logicalDistance + 3);

		//X軸に垂直の基線(一般に縦)
		baseLineX = new Line();
		baseLineY = new Line();
		
		axis.setOrigin(centers[0][0]);
		modulePitch[0] = getAreaModulePitch(centers[0][0], centers[1][0], logicalDistance + 6);
		modulePitch[1] = getAreaModulePitch(centers[0][0], centers[0][1], logicalDistance + 6);
		axis.setModulePitch(modulePitch[0]);
		axis.setOrigin(centers[0][1]);
		modulePitch[2] = getAreaModulePitch(axis.translate(0,-3), centers[1][1], logicalDistance + 3);
		axis.setModulePitch(modulePitch[1]);
		axis.setOrigin(centers[1][0]);
		modulePitch[3] = getAreaModulePitch(axis.translate(-3,0), centers[1][1], logicalDistance + 3);
		
		
		axis.setOrigin(centers[0][0]);
		axis.setModulePitch(modulePitch[0]);
		baseLineX.setP1(axis.translate(-3,-3));

		axis.setModulePitch(modulePitch[1]);
		baseLineY.setP1(axis.translate(-3,-3));
		
		axis.setOrigin(centers[0][1]);
		axis.setModulePitch(modulePitch[2]);
		baseLineX.setP2(axis.translate(-3,3));
		
		axis.setOrigin(centers[1][0]);
		axis.setModulePitch(modulePitch[3]);
		baseLineY.setP2(axis.translate(3,-3));
		

		baseLineX.translate(1,1);
		baseLineY.translate(1,1);
		
		for (int i = 0; i < sqrtNumModules; i++) {
			gridLineX = new Line(baseLineX.getP1(), baseLineX.getP2());

			axis.setOrigin(gridLineX.getP1());
			axis.setModulePitch(modulePitch[0]);
			gridLineX.setP1(axis.translate(i,0));

			axis.setOrigin(gridLineX.getP2());
			axis.setModulePitch(modulePitch[2]);
			gridLineX.setP2(axis.translate(i,0));

			
			gridLineY = new Line(baseLineY.getP1(), baseLineY.getP2());
			
			axis.setOrigin(gridLineY.getP1());
			axis.setModulePitch(modulePitch[1]);
			gridLineY.setP1(axis.translate(0,i));

			axis.setOrigin(gridLineY.getP2());
			axis.setModulePitch(modulePitch[3]);
			gridLineY.setP2(axis.translate(0,i));
			

			samplingGrid[0][0][0][i] = gridLineX;
			samplingGrid[0][0][1][i] = gridLineY;
			
		}
		for (int ay = 0; ay < samplingGrid[0].length; ay++) {
			for (int ax = 0; ax < samplingGrid.length; ax++) {
				canvas.drawLines(samplingGrid[ax][ay][0], Color.LIGHTBLUE);
				canvas.drawLines(samplingGrid[ax][ay][1], Color.LIGHTBLUE);
			}
		}
		return samplingGrid;
	}
	

	
	//型番7～13用
	Line[][][][] getSamplingGrid7_13(FinderPattern finderPattern, AlignmentPattern alignmentPattern) {

		Point centers[][] = alignmentPattern.getCenter();
		centers[0][0] = finderPattern.getCenter(FinderPattern.UL);
		centers[2][0] = finderPattern.getCenter(FinderPattern.UR);
		centers[0][2] = finderPattern.getCenter(FinderPattern.DL);
		int sqrtNumModules = finderPattern.getSqrtNumModules(); //一辺当たりのモジュール数を得る
		int sqrtNumArea = 2;
		int sqrtNumAreaModules = sqrtNumModules / sqrtNumArea;
		sqrtNumAreaModules++;
		Line[][][][] samplingGrid = new Line[sqrtNumArea][sqrtNumArea][2][sqrtNumAreaModules];
		Line baseLineX, baseLineY, gridLineX, gridLineY;

		int logicalDistance = alignmentPattern.getLogicalDistance();
		int sin = finderPattern.getAngle()[0];
		int cos = finderPattern.getAngle()[1];
		Axis axis = new Axis(sin, cos, finderPattern.getModuleSize());
		int[] modulePitch;
		for (int ay = 0; ay < sqrtNumArea; ay++) {
			for (int ax = 0; ax < sqrtNumArea; ax++) {
				modulePitch = new int[4]; //top left bottom rightの順に格納
				baseLineX = new Line();
				baseLineY = new Line();
				axis.setModulePitch(finderPattern.getModuleSize());
				if (ax == 0 && ay == 0) {
					axis.setOrigin(centers[0][0]);
					modulePitch[0] = getAreaModulePitch(axis.translate(0,3), centers[1][0], logicalDistance + 3);
					modulePitch[1] = getAreaModulePitch(axis.translate(3,0), centers[0][1], logicalDistance + 3);
					axis.setModulePitch(modulePitch[0]);
					modulePitch[2] = getAreaModulePitch(centers[0][1], centers[1][1], logicalDistance);
					axis.setModulePitch(modulePitch[1]);
					modulePitch[3] = getAreaModulePitch(centers[1][0], centers[1][1], logicalDistance);
					
					axis.setModulePitch(modulePitch[0]);
					baseLineX.setP1(axis.translate(-3,-3));

					axis.setModulePitch(modulePitch[1]);
					baseLineY.setP1(axis.translate(-3,-3));
					
					axis.setOrigin(centers[0][1]);
					axis.setModulePitch(modulePitch[2]);
					baseLineX.setP2(axis.translate(-6,0));
					
					axis.setOrigin(centers[1][0]);
					axis.setModulePitch(modulePitch[3]);
					baseLineY.setP2(axis.translate(0,-6));
				}
				else if (ax == 1 && ay == 0) {
					axis.setOrigin(centers[1][0]);
					modulePitch[0] = getAreaModulePitch(axis.translate(0,-3), centers[2][0], logicalDistance + 3);
					modulePitch[1] = getAreaModulePitch(centers[1][0], centers[1][1], logicalDistance);
					axis.setModulePitch(modulePitch[0]);
					modulePitch[2] = getAreaModulePitch(centers[1][1], centers[2][1], logicalDistance);
					axis.setModulePitch(modulePitch[1]);
					axis.setOrigin(centers[2][0]);
					modulePitch[3] = getAreaModulePitch(axis.translate(-3,0), centers[2][1], logicalDistance + 3);
					
					axis.setOrigin(centers[1][0]);
					axis.setModulePitch(modulePitch[1]);
					baseLineX.setP1(axis.translate(0,-6));

					baseLineY.setP1(axis.translate(0,-6));
					
					baseLineX.setP2(centers[1][1]);
					
					axis.setOrigin(centers[2][0]);
					axis.setModulePitch(modulePitch[3]);
					baseLineY.setP2(axis.translate(3,-3));
				}
				else if (ax == 0 && ay == 1) {
					modulePitch[0] = getAreaModulePitch(centers[0][1], centers[1][1], logicalDistance);
					axis.setOrigin(centers[0][2]);
					modulePitch[1] = getAreaModulePitch(centers[0][1], axis.translate(3,0), logicalDistance + 3);
					axis.setModulePitch(modulePitch[0]);
					modulePitch[2] = getAreaModulePitch(axis.translate(0,-3), centers[1][2], logicalDistance + 3);
					axis.setModulePitch(modulePitch[2]);
					modulePitch[3] = getAreaModulePitch(centers[1][1], centers[1][2], logicalDistance);
					
					axis.setOrigin(centers[0][1]);
					axis.setModulePitch(modulePitch[0]);
					baseLineX.setP1(axis.translate(-6,0));

					baseLineY.setP1(axis.translate(-6,0));
					
					axis.setOrigin(centers[0][2]);
					axis.setModulePitch(modulePitch[2]);
					baseLineX.setP2(axis.translate(-3, 3));
					
					baseLineY.setP2(centers[1][1]);					
				}
				else if (ax == 1 && ay == 1) {
					modulePitch[0] = getAreaModulePitch(centers[1][1], centers[2][1], logicalDistance);
					modulePitch[1] = getAreaModulePitch(centers[1][1], centers[1][2], logicalDistance);
					modulePitch[2] = getAreaModulePitch(centers[1][2], centers[2][2], logicalDistance);
					modulePitch[3] = getAreaModulePitch(centers[2][1], centers[2][2], logicalDistance);
					
					baseLineX.setP1(centers[1][1]);
					baseLineY.setP1(centers[1][1]);

					axis.setOrigin(centers[1][2]);
					axis.setModulePitch(modulePitch[1]);
					baseLineX.setP2(axis.translate(0,6));

					axis.setOrigin(centers[2][1]);
					axis.setModulePitch(modulePitch[0]);
					baseLineY.setP2(axis.translate(6,0));
				}
				


				baseLineX.translate(1,1);
				baseLineY.translate(1,1);
				
				for (int i = 0; i < sqrtNumAreaModules; i++) {
					gridLineX = new Line(baseLineX.getP1(), baseLineX.getP2());

					axis.setOrigin(gridLineX.getP1());
					axis.setModulePitch(modulePitch[0]);
					gridLineX.setP1(axis.translate(i,0));

					axis.setOrigin(gridLineX.getP2());
					axis.setModulePitch(modulePitch[2]);
					gridLineX.setP2(axis.translate(i,0));
	
					
					gridLineY = new Line(baseLineY.getP1(), baseLineY.getP2());
					
					axis.setOrigin(gridLineY.getP1());
					axis.setModulePitch(modulePitch[1]);
					gridLineY.setP1(axis.translate(0,i));

					axis.setOrigin(gridLineY.getP2());
					axis.setModulePitch(modulePitch[3]);
					gridLineY.setP2(axis.translate(0,i));
					

					samplingGrid[ax][ay][0][i] = gridLineX;
					samplingGrid[ax][ay][1][i] = gridLineY;
					
				}
			}
		}
		for (int ay = 0; ay < samplingGrid[0].length; ay++) {
			for (int ax = 0; ax < samplingGrid.length; ax++) {
				canvas.drawLines(samplingGrid[ax][ay][0], Color.LIGHTBLUE);
				canvas.drawLines(samplingGrid[ax][ay][1], Color.LIGHTBLUE);
			}
		}
		return samplingGrid;
	}
	//型番14～20用
//	Line[][][][] getSamplingGrid14_20(FinderPattern finderPattern, AlignmentPattern alignmentPattern) {
//
//		Point centers[][] = alignmentPattern.getCenter();
//		centers[0][0] = finderPattern.getCenter(FinderPattern.UL);
//		centers[3][0] = finderPattern.getCenter(FinderPattern.UR);
//		centers[0][3] = finderPattern.getCenter(FinderPattern.DL);
//		int sqrtNumModules = finderPattern.getSqrtNumModules(); //一辺当たりのモジュール数を得る
//		int sqrtNumArea = 3;
//		int sqrtNumAreaModules = sqrtNumModules / sqrtNumArea;
//		sqrtNumAreaModules+=3;
//		Line[][][][] samplingGrid = new Line[sqrtNumArea][sqrtNumArea][2][sqrtNumAreaModules];
//		Line baseLineX, baseLineY, gridLineX, gridLineY;
//		Point[] targetCenters;
//		int logicalDistance = alignmentPattern.getLogicalDistance();
//		int sin = finderPattern.getAngle()[0];
//		int cos = finderPattern.getAngle()[1];
//		Axis axis = new Axis(sin, cos, finderPattern.getModuleSize());
//		int[] modulePitch;
//		for (int ay = 0; ay < sqrtNumArea; ay++) {
//			for (int ax = 0; ax < sqrtNumArea; ax++) {
//				modulePitch = new int[4]; //top left bottom rightの順に格納
//				baseLineX = new Line();
//				baseLineY = new Line();
//				axis.setModulePitch(finderPattern.getModuleSize());
//				if (ax == 0 && ay == 0) {
//					axis.setOrigin(centers[0][0]);
//					modulePitch[0] = getAreaModulePitch(axis.translate(0,3), centers[1][0], logicalDistance + 3);
//					modulePitch[1] = getAreaModulePitch(axis.translate(3,0), centers[0][1], logicalDistance + 3);
//					axis.setModulePitch(modulePitch[0]);
//					modulePitch[2] = getAreaModulePitch(centers[0][1], centers[1][1], logicalDistance);
//					axis.setModulePitch(modulePitch[1]);
//					modulePitch[3] = getAreaModulePitch(centers[1][0], centers[1][1], logicalDistance);
//					
//					axis.setModulePitch(modulePitch[0]);
//					baseLineX.setP1(axis.translate(-3,-3));
//
//					axis.setModulePitch(modulePitch[1]);
//					baseLineY.setP1(axis.translate(-3,-3));
//					
//					axis.setOrigin(centers[0][1]);
//					axis.setModulePitch(modulePitch[2]);
//					baseLineX.setP2(axis.translate(-6,0));
//					
//					axis.setOrigin(centers[1][0]);
//					axis.setModulePitch(modulePitch[3]);
//					baseLineY.setP2(axis.translate(0,-6));
//				}
//				else if (ax == 1 && ay == 0) {
//					modulePitch[0] = getAreaModulePitch(centers[1][0], centers[2][0], logicalDistance);
//					modulePitch[1] = getAreaModulePitch(centers[1][0], centers[1][1], logicalDistance);
//					modulePitch[2] = getAreaModulePitch(centers[1][1], centers[2][1], logicalDistance);
//					modulePitch[3] = getAreaModulePitch(centers[2][0], centers[2][1], logicalDistance);
//					
//					//baseLineX.setP1(centers[1][0]);
//					//baseLineY.setP1(centers[1][0]);
//
//					axis.setOrigin(centers[1][0]);
//					axis.setModulePitch(modulePitch[1]);
//					baseLineX.setP1(axis.translate(0,-6));
//
//					baseLineX.setP2(centers[1][1]);
//					
//					axis.setOrigin(centers[1][0]);
//					axis.setModulePitch(modulePitch[1]);
//					baseLineY.setP1(axis.translate(0,-6));
//
//					axis.setOrigin(centers[2][0]);
//					axis.setModulePitch(modulePitch[3]);
//					baseLineY.setP2(axis.translate(0,-6));
//					
//
//				}
//				else if (ax == 2 && ay == 0) {
//					axis.setOrigin(centers[2][0]);
//					modulePitch[0] = getAreaModulePitch(axis.translate(0,-3), centers[3][0], logicalDistance + 3);
//					modulePitch[1] = getAreaModulePitch(centers[2][0], centers[2][1], logicalDistance);
//					axis.setModulePitch(modulePitch[0]);
//					modulePitch[2] = getAreaModulePitch(centers[2][1], centers[3][1], logicalDistance);
//					axis.setModulePitch(modulePitch[1]);
//					axis.setOrigin(centers[3][0]);
//					modulePitch[3] = getAreaModulePitch(axis.translate(-3,0), centers[3][1], logicalDistance + 3);
//					
//					axis.setOrigin(centers[2][0]);
//					axis.setModulePitch(modulePitch[1]);
//					baseLineX.setP1(axis.translate(0,-6));
//
//					baseLineY.setP1(axis.translate(0,-6));
//					
//					baseLineX.setP2(centers[2][1]);
//					
//					axis.setOrigin(centers[3][0]);
//					axis.setModulePitch(modulePitch[3]);
//					baseLineY.setP2(axis.translate(3,-3));
//				}
//				else if (ax == 0 && ay == 1) {
//					modulePitch[0] = getAreaModulePitch(centers[0][1], centers[1][1], logicalDistance);
//					modulePitch[1] = getAreaModulePitch(centers[0][1], centers[0][2], logicalDistance);
//					modulePitch[2] = getAreaModulePitch(centers[0][2], centers[1][2], logicalDistance);
//					modulePitch[3] = getAreaModulePitch(centers[1][1], centers[1][2], logicalDistance);
//					
//					axis.setOrigin(centers[0][1]);
//					axis.setModulePitch(modulePitch[0]);
//					baseLineX.setP1(axis.translate(-6,0));
//
//					baseLineY.setP1(axis.translate(-6,0));
//					
//					axis.setOrigin(centers[0][2]);
//					axis.setModulePitch(modulePitch[2]);
//					baseLineX.setP2(axis.translate(-6, 0));
//					
//					baseLineY.setP2(centers[1][1]);					
//				}
//				else if (ax == 1 && ay == 1) {
//					modulePitch[0] = getAreaModulePitch(centers[1][1], centers[2][1], logicalDistance);
//					modulePitch[1] = getAreaModulePitch(centers[1][1], centers[1][2], logicalDistance);
//					modulePitch[2] = getAreaModulePitch(centers[1][2], centers[2][2], logicalDistance);
//					modulePitch[3] = getAreaModulePitch(centers[2][1], centers[2][2], logicalDistance);
//					
//					baseLineX.setP1(centers[1][1]);
//					baseLineY.setP1(centers[1][1]);
//
//					baseLineX.setP2(centers[1][2]);
//
//					baseLineY.setP2(centers[2][1]);
//				}
//				else if (ax == 2 && ay == 1) {
//					modulePitch[0] = getAreaModulePitch(centers[2][1], centers[3][1], logicalDistance);
//					modulePitch[1] = getAreaModulePitch(centers[2][1], centers[2][2], logicalDistance);
//					modulePitch[2] = getAreaModulePitch(centers[2][2], centers[3][2], logicalDistance);
//					modulePitch[3] = getAreaModulePitch(centers[3][1], centers[3][2], logicalDistance);
//					
//					baseLineX.setP1(centers[2][1]);
//					baseLineY.setP1(centers[2][1]);
//					baseLineX.setP2(centers[2][2]);
//
//					
//					axis.setOrigin(centers[3][1]);
//					axis.setModulePitch(modulePitch[0]);
//					baseLineY.setP2(axis.translate(6, 0));
//				}
//				else if (ax == 0 && ay == 2) {
//					modulePitch[0] = getAreaModulePitch(centers[0][2], centers[1][2], logicalDistance);
//					axis.setOrigin(centers[0][3]);
//					modulePitch[1] = getAreaModulePitch(centers[0][2], axis.translate(3,0), logicalDistance + 3);
//					//axis.setModulePitch(modulePitch[0]);
//					modulePitch[2] = getAreaModulePitch(axis.translate(0,-3), centers[1][3], logicalDistance + 3);
//					//axis.setModulePitch(modulePitch[2]);
//					modulePitch[3] = getAreaModulePitch(centers[1][2], centers[1][3], logicalDistance);
//					
//					
//					axis.setOrigin(centers[0][2]);
//					axis.setModulePitch(modulePitch[0]);
//					baseLineX.setP1(axis.translate(-6,0));
//					baseLineY.setP1(axis.translate(-6,0));
//					axis.setOrigin(centers[0][3]);
//					axis.setModulePitch(modulePitch[2]);
//					baseLineX.setP2(axis.translate(-3, 3));
//					
//
//					
//					baseLineY.setP2(centers[1][2]);			
//				}
//				else if (ax == 1 && ay == 2) {
//					modulePitch[0] = getAreaModulePitch(centers[1][2], centers[2][2], logicalDistance);
//					modulePitch[1] = getAreaModulePitch(centers[1][2], centers[1][3], logicalDistance);
//					modulePitch[2] = getAreaModulePitch(centers[1][3], centers[2][3], logicalDistance);
//					modulePitch[3] = getAreaModulePitch(centers[2][2], centers[2][3], logicalDistance);
//					
//					baseLineX.setP1(centers[1][2]);
//					baseLineY.setP1(centers[1][2]);
//
//					axis.setOrigin(centers[1][3]);
//					axis.setModulePitch(modulePitch[1]);
//					baseLineX.setP2(axis.translate(0,6));
//
//					
//					baseLineY.setP2(centers[2][2]);
//				}
//				else if (ax == 2 && ay == 2) {
//					modulePitch[0] = getAreaModulePitch(centers[2][2], centers[3][2], logicalDistance);
//					modulePitch[1] = getAreaModulePitch(centers[2][2], centers[2][3], logicalDistance);
//					modulePitch[2] = getAreaModulePitch(centers[2][3], centers[3][3], logicalDistance);
//					modulePitch[3] = getAreaModulePitch(centers[3][2], centers[3][3], logicalDistance);
//					
//					baseLineX.setP1(centers[2][2]);
//					baseLineY.setP1(centers[2][2]);
//
//					axis.setOrigin(centers[2][3]);
//					axis.setModulePitch(modulePitch[1]);
//					baseLineX.setP2(axis.translate(0,6));
//
//					axis.setOrigin(centers[3][2]);
//					axis.setModulePitch(modulePitch[0]);
//					baseLineY.setP2(axis.translate(6,0));
//				}
//				
//
//
//				baseLineX.translate(1,1);
//				baseLineY.translate(1,1);
//				
//				for (int i = 0; i < sqrtNumAreaModules; i++) {
//					gridLineX = new Line(baseLineX.getP1(), baseLineX.getP2());
//
//					axis.setOrigin(gridLineX.getP1());
//					axis.setModulePitch(modulePitch[0]);
//					gridLineX.setP1(axis.translate(i,0));
//
//					axis.setOrigin(gridLineX.getP2());
//					axis.setModulePitch(modulePitch[2]);
//					gridLineX.setP2(axis.translate(i,0));
//	
//					
//					gridLineY = new Line(baseLineY.getP1(), baseLineY.getP2());
//					
//					axis.setOrigin(gridLineY.getP1());
//					axis.setModulePitch(modulePitch[1]);
//					gridLineY.setP1(axis.translate(0,i));
//
//					axis.setOrigin(gridLineY.getP2());
//					axis.setModulePitch(modulePitch[3]);
//					gridLineY.setP2(axis.translate(0,i));
//					
//
//					samplingGrid[ax][ay][0][i] = gridLineX;
//					samplingGrid[ax][ay][1][i] = gridLineY;
//					
//				}
//			}
//		}
//		for (int ay = 0; ay < samplingGrid[0].length; ay++) {
//			for (int ax = 0; ax < samplingGrid.length; ax++) {
//				canvas.drawLines(samplingGrid[ax][ay][0], Color.LIGHTBLUE);
//				canvas.drawLines(samplingGrid[ax][ay][1], Color.LIGHTBLUE);
//			}
//		}
//		return samplingGrid;
//	}
	
	//領域内のモジュールピッチを得る
	int getAreaModulePitch(Point start, Point end, int logicalDistance) {
		Line tempLine;
		tempLine = new Line(start, end);
		int realDistance = tempLine.getLength();
		int modulePitch = (realDistance << DECIMAL_POINT) / logicalDistance;
		return modulePitch;
	}

	
	//gridLines[areaX][areaY][direction(x=0,y=1)][EachLines]	
	boolean[][] getQRCodeMatrix(boolean[][] image, Line[][][][] gridLines) throws ArrayIndexOutOfBoundsException {
		int gridSize = gridLines.length * gridLines[0][0][0].length;
		if (gridLines.length >= 2)
			gridSize--;
		boolean[][] sampledMatrix = new boolean[gridSize][gridSize];
		for (int ay = 0; ay < gridLines[0].length; ay++) {
			for (int ax = 0; ax < gridLines.length; ax++) {
				for (int y = 0; y < gridLines[0][0][1].length; y++) {
					for (int x = 0; x < gridLines[0][0][0].length; x++) {
						int x1 = gridLines[ax][ay][0][x].getP1().getX();
						int y1 = gridLines[ax][ay][0][x].getP1().getY();
						int x2 = gridLines[ax][ay][0][x].getP2().getX();
						int y2 = gridLines[ax][ay][0][x].getP2().getY();
						int x3 = gridLines[ax][ay][1][y].getP1().getX();
						int y3 = gridLines[ax][ay][1][y].getP1().getY();
						int x4 = gridLines[ax][ay][1][y].getP2().getX();
						int y4 = gridLines[ax][ay][1][y].getP2().getY();
						
						int e = (y2 - y1) * (x3 - x4) - (y4 - y3) * (x1 - x2);
						int f = (x1 * y2 - x2 * y1) * (x3 - x4) - (x3 * y4 - x4 * y3) * (x1 - x2);
						int g = (x3 * y4 - x4 * y3) * (y2 - y1) - (x1 * y2 - x2 * y1) * (y4 - y3);
						try {
							sampledMatrix[ax * gridLines[0][0][0].length + x - ax][ay * gridLines[0][0][1].length + y - ay] = 
								image[f / e][g / e];
						}
						catch (ArrayIndexOutOfBoundsException exception) {
							sampledMatrix[ax * gridLines[0][0][0].length + x - ax][ay * gridLines[0][0][1].length + y - ay] = 
								POINT_LIGHT;					
						}
						//canvas.drawPoint(new Point(f /e, g /e), Color.WHITE);
					}
				}
				
			}
		}

		return sampledMatrix;
	}
}