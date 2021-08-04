package com.playsawdust.chipper.glow.image.io;

import java.io.IOException;

import com.playsawdust.chipper.glow.image.ImageData;
import com.playsawdust.chipper.glow.image.vector.Vec2l;

public interface WindowedImageInput {
	/**
	 * Gets the size of the entire image, i.e. the windowable coordinate space
	 */
	public Vec2l getSize();
	
	/**
	 * Loads part of the image into an ImageData, wrapping the image if necessary to the window size. Every pixel in the
	 * image will represent a pixel in the source image.
	 * @param x      The X coordinate of the first pixel inside the window
	 * @param y      The Y coordinate of the first pixel inside the window
	 * @param width  The width of the window in pixels
	 * @param height The height of the window in pixels
	 * @return       An ImageData containing the portion of the source image that lies within the window. The returned
	 *               ImageData will be width by height pixels, even if it overhangs the edge of the source image.
	 */
	public ImageData loadWindow(long x, long y, int width, int height) throws IOException;
	
	/**
	 * Closes this loader. Will call close on any underlying DataSlice or stream.
	 */
	public void close() throws IOException;
}
