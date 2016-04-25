package org.hackyourlife.gcn.dsp;

public interface Stream {
	public boolean hasMoreData();
	public byte[] decode() throws Exception;
	public short[] decode16() throws Exception;
	public int getChannels();
	public long getSampleRate();
	public void reset() throws Exception;
	public void close() throws Exception;
	public long getPreferredBufferSize();
}
