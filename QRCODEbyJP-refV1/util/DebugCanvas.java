package jp.sourceforge.qrcode.codec.util;

import jp.sourceforge.qrcode.codec.geom.Line;
import jp.sourceforge.qrcode.codec.geom.Point;

import java.awt.Canvas;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class DebugCanvas extends Canvas {
	BufferedImage image;
	static DebugCanvas instance;
	public void setFontColor(int color){};

	public void paint(Graphics g){
		if (image != null)
			g.drawImage(image, 0, 0, java.awt.Color.WHITE, null);
	}
	
	DebugCanvas() {
		//image = new BufferedImage(0, 0, BufferedImage.TYPE_INT_RGB);
	}
	
	public static DebugCanvas getCanvas(){
		if (instance == null)
			instance = new DebugCanvas();
		return instance;
	}

	public  int getLineWidth(){return 0;}

	//êVÇµÇ≠çsÇí«â¡Ç∑ÇÈ
	public  void println(String string){
		System.out.println(string);
	}

	public  void println(int num){
		System.out.println(num);
	}

	public  void println(Object object){
		System.out.println(object);
	}

	public  void print(String string){
		System.out.print(string);
	}

	public  void print(Object object){
		System.out.print(object);
	}

	public  void drawMatrix(boolean[][] matrix) {
		if (image == null) {
			image = new BufferedImage(matrix.length, matrix[0].length, BufferedImage.TYPE_INT_ARGB);
			setSize(matrix.length, matrix[0].length);
		}
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(java.awt.Color.WHITE);
		int width = getWidth();
		for (int x = 0; x < matrix.length; x++) {
			g2d.drawLine(x, 0, x, width);
		}
		g2d.setColor(java.awt.Color.BLACK);
		for (int x = 0; x < matrix.length; x++) {
			for (int y = 0; y < matrix[0].length; y++) {
				if (matrix[x][y] == true)
					g2d.drawLine(x, y, x, y);
			}
		}
		repaint();
	}
	
	public  void drawMatrix(boolean[][] matrix, int ratio){
	}

	public  void drawLines(Line[] lines, int color){
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(convertColor(color));
		for (int i = 0; i < lines.length; i++) {
			g2d.drawLine(lines[i].getP1().getX(), lines[i].getP1().getY(),
					lines[i].getP2().getX(), lines[i].getP2().getY());
		}
		repaint();

	}
	
	java.awt.Color convertColor(int color) {
		java.awt.Color awtColor =  java.awt.Color.BLACK;
		switch(color) {
		case Color.LIGHTGREEN:
			awtColor = java.awt.Color.GREEN;
			break;
		case Color.LIGHTBLUE:
			awtColor = java.awt.Color.blue;
			break;
		case Color.LIGHTRED:
			awtColor = java.awt.Color.RED;
			break;
		}
		return awtColor;
		
	}

	public  void drawPolygon(Point[] points, int color){
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(convertColor(color));
		int numPoints = points.length;
		int[] polygonX = new int[numPoints];
		int[] polygonY = new int[numPoints];
		for (int i = 0; i < numPoints; i++) {
			polygonX[i] = points[i].getX();
			polygonY[i] = points[i].getY();
		}
		g2d.drawPolygon(polygonX, polygonY, numPoints);
		repaint();

	}

	public  void drawPoints(Point[] points, int color){
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(convertColor(color));
		for (int i = 0; i < points.length; i++)
			g2d.drawLine(points[i].getX(), points[i].getY(),points[i].getX(), points[i].getY());
		repaint();

	}

	public  void drawCross(Point point, int color){
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(convertColor(color));
		int x = point.getX();
		int y = point.getY();
		g2d.drawLine(x - 5, y, x + 5, y);
		g2d.drawLine(x, y - 5, x ,y + 5);
		repaint();

	}
	public BufferedImage getImage() {
		return image;
	}
	public void setImage(BufferedImage image) {
		this.image = image;
	}
}