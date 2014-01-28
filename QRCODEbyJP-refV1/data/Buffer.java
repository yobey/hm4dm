package jp.sourceforge.qrcode.codec.data;

import java.util.*;

//最近入力されたObjectを制限容量まで保持するBuffer。
//溢れたデータは破棄される。

public class Buffer{
	Vector elementData;
	int capacity;
	public Buffer(int capacity) {
		elementData = new Vector();
		this.capacity = capacity;
	}
	public void addElement(Object element) {
		elementData.addElement(element);
		if (elementData.size() == capacity + 1)
			elementData.removeElement(elementData.firstElement());
	}
	
	public Object popFirstElement() {
		Object element = elementData.firstElement();
		elementData.removeElement(elementData.firstElement());
		return element;
	}
	public Object popLastElement() {
		Object element = elementData.lastElement();
		elementData.removeElement(elementData.lastElement());
		return element;
		
	}
	
	public Object elementAt(int index) {
		return elementData.elementAt(index);
	}
	
	public Object lastElement() {
		return elementData.lastElement();
	}
	
	public boolean isEmpty() {
		return elementData.isEmpty();
	}
	
	public int capacity() {
		return capacity;
	}
	
	public int size() {
		return elementData.size();
	}
	
	public void removeAllElements() {
		elementData.removeAllElements();
	}
}
