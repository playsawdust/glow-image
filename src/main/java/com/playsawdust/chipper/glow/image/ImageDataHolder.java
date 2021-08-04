package com.playsawdust.chipper.glow.image;

/**
 * @see ImageData
 * @see DeepColorImageData
 */
public interface ImageDataHolder {
	public int getPixel(int x, int y);
	public long getDeepPixel(int x, int y);
	public void setPixel(int x, int y, int color);
	public void setDeepPixel(int x, int y, long color);
}
