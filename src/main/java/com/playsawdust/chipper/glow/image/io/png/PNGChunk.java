package com.playsawdust.chipper.glow.image.io.png;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;

public abstract class PNGChunk {
	
	public static PNGChunk readChunk(DataInputStream in) throws IOException {
		int length = in.readInt();
		int chunkType = in.readInt();
		switch(chunkType) {
		case IHDRChunk.TYPE_TAG:
			IHDRChunk ihdrChunk = new IHDRChunk();
			ihdrChunk.read(in);
			/*
			ihdrChunk.width = in.readInt();
			ihdrChunk.height = in.readInt();
			ihdrChunk.bitDepth = in.read();
			ihdrChunk.colorType = in.read();
			ihdrChunk.compression = in.read();
			ihdrChunk.filterMethod = in.read();
			ihdrChunk.interlaceMethod = in.read();
			in.readInt(); //throw away the CRC
			*/
			return ihdrChunk;
		case IDATChunk.TYPE_TAG:
			byte[] idatData = new byte[length];
			in.readFully(idatData);
			in.readInt(); //throw away the CRC
			IDATChunk idat = new IDATChunk();
			idat.data = idatData;
			return idat;
		case IENDChunk.TYPE_TAG:
			if (length>0) in.skip(length); //Specified to never happen but let's check anyway
			in.readInt(); //throw away the CRC
			return new IENDChunk();
		default:
			byte[] data = new byte[length];
			in.readFully(data);
			in.readInt(); //throw away the CRC
			RawChunk rawChunk = new RawChunk();
			rawChunk.chunkType = chunkType;
			rawChunk.data = data;
			return rawChunk;
		}
	}
	
	public abstract int getChunkType();
	
	public void writeChunk(DataOutputStream out) throws IOException {
		
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		CheckedOutputStream checked = new CheckedOutputStream(baos, new CRC32());
		DataOutputStream dataOut = new DataOutputStream(checked);
		
		//ChunkType and all the chunk data are covered by the crc
		dataOut.writeInt(getChunkType());
		writeChunkData(dataOut);
		
		dataOut.flush();
		checked.flush();
		int finalCrc = (int) checked.getChecksum().getValue();
		baos.flush();
		byte[] toWrite = baos.toByteArray();
		
		out.writeInt(toWrite.length-4);
		out.write(toWrite);
		out.writeInt(finalCrc);
		
	}
	
	public abstract void writeChunkData(DataOutputStream out) throws IOException;
}