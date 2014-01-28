/*
 * �쐬���F 2004/09/25
 *
 * TODO ���̐������ꂽ�t�@�C���̃e���v���[�g��ύX����ɂ͎����Q�ƁB
 * �E�B���h�E �� �ݒ� �� Java �� �R�[�h�E�X�^�C�� �� �R�[�h�E�e���v���[�g
 */
package jp.sourceforge.qrcode.codec.data;

import jp.sourceforge.qrcode.codec.geom.*;

/**
 * @author Owner
 *
 * TODO ���̐������ꂽ�^�R�����g�̃e���v���[�g��ύX����ɂ͎����Q�ƁB
 * �E�B���h�E �� �ݒ� �� Java �� �R�[�h�E�X�^�C�� �� �R�[�h�E�e���v���[�g
 */
public class QRCodeSymbol {
	int version;
	int errorCollectionLevel;
	int maskPattern;
	int dataCapacity;
	boolean[][] matrix;
	int width, height;
	Point[][] alignmentPattern;
	final int[][] numErrorCollectionCode = {
			{7,10,13,17},{10,16,22,28},{15,26,36,44},{20,36,52,64},
			{26,48,72,88},{36,64,96,112},{40,72,108,130},{48,88,132,156},
			{60,110,160,192},{72,130,192,224},{80,150,224,264},{96,176,260,308},
			{104,198,288,352}
	};

	final int[][] numRSBlocks = {
			{1,1,1,1},{1,1,1,1},{1,1,2,2},{1,2,2,4},
			{1,2,4,4},{2,4,4,4},{2,4,6,5},{2,4,6,6},
			{2,5,8,8},{4,5,8,8},{4,5,8,11},{4,8,10,11},
			{4,9,12,16}
	};
	
	public boolean getElement(int x, int y) {
		return matrix[x][y];
	}
	
	
	public int getNumErrorCollectionCode() {
		return numErrorCollectionCode[version - 1][errorCollectionLevel];
	}
	
	public int getNumRSBlocks() {
		return numRSBlocks[version - 1][errorCollectionLevel];
	}
	
	public QRCodeSymbol(boolean[][] matrix) {
		this.matrix = matrix;
		width = matrix.length;
		height = matrix[0].length;
		initialize();
	}
	

	
	void initialize() {
		//�����W���[�������^�ԎZ�o
		version = (width - 17) / 4;
		//System.out.println("version" + String.valueOf(version));
		//alignmentPattern = createAlignmentPatternPosition(version);
		
		Point[][] alignmentPattern = new Point[1][1];
		
		int[] logicalSeeds = new int[1];
		//���΍��W�̌��ɂȂ����W�܂��͍s���W�̍쐬
		if (version >= 2 && version <= 6) {
			logicalSeeds = new int[2];
			logicalSeeds[0] = 6;
			logicalSeeds[1] = 10 + 4 * version;
			alignmentPattern = new Point[logicalSeeds.length][logicalSeeds.length];
		}
		else if (version >= 7 && version <= 13) {
			logicalSeeds = new int[3];
			logicalSeeds[0] = 6;
			logicalSeeds[1] = 8 + 2 * version;
			logicalSeeds[2] = 10 + 4 * version;
			alignmentPattern = new Point[logicalSeeds.length][logicalSeeds.length];
		}
		
		
		//���ۂ̑��΍��W�̍쐬
		for (int col = 0; col < logicalSeeds.length; col++) { //��@
			for (int row = 0; row < logicalSeeds.length; row++) { //�s
				alignmentPattern[row][col] = new Point(logicalSeeds[row], logicalSeeds[col]);
			}
		}
		this.alignmentPattern = alignmentPattern;
		//�f�[�^�e�ʂ����߂�
		dataCapacity = calcDataCapacity();
	}
	public int getVersion() {
		return version;
	}
	public String getVersionReference() {
		final char[] versionReferenceCharacter = {'L', 'M', 'Q', 'H'};
		
		return Integer.toString(version)+ "-" + 
			versionReferenceCharacter[errorCollectionLevel];
	}
	
	public Point[][] getAlignmentPattern() {
		return alignmentPattern;
	}
	
	private int calcDataCapacity() {
		int numFunctionPatternModule = 0;
		int numFormatAndVersionInfoModule = 0;
		int version = this.getVersion();
		//System.out.println("Version:" + String.valueOf(version));
		if (version == 1)
			numFunctionPatternModule = 202;
		else if (version >= 2 && version <= 6)
			numFunctionPatternModule = 219 + 8 * version;
		else if (version >= 7 && version <= 13)
			numFunctionPatternModule = 334 + 8 * version;
		else if (version >= 14 && version <= 20)
			numFunctionPatternModule = 499 + 8 * version;

		if (version <= 6)
			numFormatAndVersionInfoModule = 31;
		else
			numFormatAndVersionInfoModule = 67;
		
		int dataCapacity = (width * width - numFunctionPatternModule - numFormatAndVersionInfoModule) / 8;
		
		return dataCapacity;
	}
	
	public int getDataCapacity() {
		return this.dataCapacity;
	}
	
	public void setFormatInformation(boolean[] formatInformation) {
		if (formatInformation[4] == false)
			if (formatInformation[3] == true)
				errorCollectionLevel = 0;
			else
				errorCollectionLevel = 1;
		else
			if (formatInformation[3] == true)
				errorCollectionLevel = 2;
			else
				errorCollectionLevel = 3;

		for (int i = 2; i >= 0; i--)
			if (formatInformation[i] == true)
				maskPattern += 1 << i;
	}
	public int getErrorCollectionLevel() {
		return errorCollectionLevel;
	}
	public int getMaskPatternReferer() {
		return maskPattern;
	}
	
	public int getWidth() {
		return width;
	}
	public int getHeight() {
		return height;
	}
	public void reverseElement(int x, int y) {
		matrix[x][y] = !matrix[x][y];
	}
	public boolean isInFunctionPattern(int targetX, int targetY) {
		if (targetX < 9 && targetY < 9) //����̈ʒu�v�f���o�p�^�[������т��̎��ӂ̋@�\�p�^�[����
			return true;
		if (targetX > getWidth() - 9 && targetY < 9) //�E��̈ʒu�v�f���o�p�^�[������т��̎��ӂ̋@�\�p�^�[����
			return true;
		if (targetX < 9  && targetY > getHeight() - 9) //�����̈ʒu�v�f���o�p�^�[������т��̎��ӂ̋@�\�p�^�[����
			return true;
		
		if (version >= 7) {
			if (targetX > getWidth() - 12  && targetY < 6)
				return true;
			if (targetX < 6 && targetY > getHeight() - 12)
				return true;
		}
		//�^�C�~���O�p�^�[����
		if (targetX == 6 || targetY == 6)
			return true;

		//�ʒu�����p�^�[�����BVector���̃f�[�^���ʒu�v�f���o�p�^�[���̂��̂����ʒu�����p�^�[���̂��̂���
		//�l�����Ȃ����A�����ʒu�v�f���o�p�^�[�����ɂ���Ȃ炱���܂œ��B���Ȃ�
		
		Point[][] alignmentPattern = getAlignmentPattern();
		int sideLength = alignmentPattern.length;

		for (int y = 0; y < sideLength; y++) {
			for (int x = 0; x < sideLength; x++) {
					if (!(x == 0 && y == 0) && !(x == sideLength - 1 && y == 0) && !(x == 0 && y == sideLength - 1)) 
					if (Math.abs(alignmentPattern[x][y].getX() - targetX) < 3 &&
							Math.abs(alignmentPattern[x][y].getY() - targetY) < 3)
						return true;
			}
		}

		return false;
	}
}
