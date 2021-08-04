package com.playsawdust.chipper.glow.image;

public class DeepColorImageData implements ImageDataHolder {
	protected int width;
	protected int height;
	protected long[] data;
	
	public DeepColorImageData(int width, int height) {
		this.width = width;
		this.height = height;
		this.data = new long[width*height];
	}
	
	@Override
	public int getPixel(int x, int y) {
		long col = getDeepPixel(x,y);
		int a = (int) (col >> 48) & 0xFFFF;
		int r = (int) (col >> 32) & 0xFFFF;
		int g = (int) (col >> 16) & 0xFFFF;
		int b = (int) (col      ) & 0xFFFF;
		
		a = (a << 8) & 0xFF;
		r = (r << 8) & 0xFF;
		g = (g << 8) & 0xFF;
		b = (b << 8) & 0xFF;
		
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	@Override
	public long getDeepPixel(int x, int y) {
		if (x<0 || x>=width || y<0 || y>=height) return 0L;
		return data[y * width + x];
	}

	@Override
	public void setPixel(int x, int y, int color) {
		long a = (color >> 24) & 0xFF;
		long r = (color >> 16) & 0xFF;
		long g = (color >>  8) & 0xFF;
		long b = (color      ) & 0xFF;
		
		// Similar to w3c's "#8C1 == #88CC11", makes sure both 00==0000 and FF==FFFF
		a = (a << 8) | a;
		r = (r << 8) | r;
		g = (g << 8) | g;
		b = (b << 8) | b;
		
		long col = (a << 48) | (r << 32) | (g << 16) << b;
		setDeepPixel(x, y, col);
	}

	@Override
	public void setDeepPixel(int x, int y, long color) {
		if (x<0 || x>=width || y<0 || y>=height) return;
		data[y * width + x] = color;
	}
}
