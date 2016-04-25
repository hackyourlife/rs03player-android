package org.hackyourlife.gcn.dsp;
import java.io.RandomAccessFile;
import java.io.IOException;

public class DSP implements Stream {
	public final static int HEADER_SIZE = 0x60;

	long	sample_count;
	long	nibble_count;
	long	sample_rate;
	int	loop_flag;
	int	format;
	long	loop_start_offset;
	long	loop_end_offset;
	long	ca;
	int	coef[]; /* really 8x2 */
	int	gain;
	int	initial_ps;
	int	initial_hist1;
	int	initial_hist2;
	int	loop_ps;
	int	loop_hist1;
	int	loop_hist2;

	RandomAccessFile file;
	DSP	ch2;
	long	filepos;
	long	filesize;
	long	startoffset;

	long	current_sample;

	ADPCMDecoder decoder;

	public DSP(RandomAccessFile file)
			throws FileFormatException, IOException {
		ch2 = null;
		this.file = file;
		filesize = file.length();
		readHeader();
	}

	public DSP(RandomAccessFile ch1, RandomAccessFile ch2)
			throws FileFormatException, IOException {
		this(ch1);
		this.ch2 = new DSP(ch2);
		validateChannel(this.ch2);
	}

	public final static int unsigned2signed16bit(int x) {
		int sign = x & (1 << 15);
		int value = x & ~(1 << 15);
		return (sign != 0) ? -(~x & 0xFFFF) : value;
	}

	public boolean read_dsp_header(byte[] header) {
		int i;

		coef = new int[16];

		sample_count =
			endianess.get_32bitBE(header, 0x00);
		nibble_count =
			endianess.get_32bitBE(header, 0x04);
		sample_rate =
			endianess.get_32bitBE(header, 0x08);
		loop_flag =
			endianess.get_16bitBE(header, 0x0c);
		format =
			endianess.get_16bitBE(header, 0x0e);
		loop_start_offset =
			endianess.get_32bitBE(header, 0x10) / 16 * 8;
		loop_end_offset =
			endianess.get_32bitBE(header, 0x14) / 16 * 8;
		ca =
			endianess.get_32bitBE(header, 0x18);
		for(i = 0; i < 16; i++)
			coef[i] =
				unsigned2signed16bit(endianess.get_16bitBE(header, 0x1c+i*2));
		gain =
			endianess.get_16bitBE(header, 0x3c);
		initial_ps =
			endianess.get_16bitBE(header, 0x3e);
		initial_hist1 =
			unsigned2signed16bit(endianess.get_16bitBE(header, 0x40));
		initial_hist2 =
			unsigned2signed16bit(endianess.get_16bitBE(header, 0x42));
		loop_ps =
			endianess.get_16bitBE(header, 0x44);
		loop_hist1 =
			unsigned2signed16bit(endianess.get_16bitBE(header, 0x46));
		loop_hist2 =
			unsigned2signed16bit(endianess.get_16bitBE(header, 0x48));

		if(sample_count > nibble_count)
			return(false);
		if((sample_rate == 0) || (sample_rate < 0))
			return(false);
		if(loop_start_offset > loop_end_offset)
			return(false);
		return(true);
	}

	private void validateChannel(DSP ch) throws FileFormatException {
		boolean invalid = false;
		if(ch.sample_count != sample_count)
			invalid = true;
		if(ch.nibble_count != nibble_count)
			invalid = true;
		if(ch.sample_rate != sample_rate)
			invalid = true;
		if(ch.loop_flag != loop_flag)
			invalid = true;
		if(ch.loop_start_offset != loop_start_offset)
			invalid = true;
		if(ch.loop_end_offset != loop_end_offset)
			invalid = true;
		if(invalid)
			throw new FileFormatException("channels do not match");
	}

	public void reset() throws IOException {
		decoder.setHistory(initial_hist1, initial_hist2);
		filepos = startoffset;
		current_sample = 0;
		seek(filepos);
		if(ch2 != null)
			ch2.reset();
	}
	public long getSampleRate() {
		return(sample_rate);
	}

	public int getChannels() {
		if(ch2 == null)
			return(1);
		else
			return(2);
	}

	private void readHeader() throws FileFormatException, IOException {
		seek(0);
		startoffset = 0x60;
		byte[] header = new byte[0x60];
		file.read(header);
		if(!read_dsp_header(header))
			throw new FileFormatException("not a devkit DSP file");
		decoder = new ADPCMDecoder();
		decoder.setCoef(coef);
		decoder.setHistory(initial_hist1, initial_hist2);
		filepos = startoffset;
		current_sample = 0;
	}

	public void close() throws IOException {
		file.close();
		if(ch2 != null) {
			ch2.close();
			ch2 = null;
		}
	}

	private void seek(long offset) throws IOException {
		file.seek(offset);
	}

	public boolean hasMoreData() {
		return(filepos < filesize);
	}

	public byte[] decode() throws IOException {
		int[] samples = doDecode();
		byte[] buffer = new byte[samples.length * 2];
		for(int i = 0; i < samples.length; i++)
			endianess.set16bit_BE(samples[i], buffer, i * 2);
		return(buffer);
	}

	public int[] doDecode() throws IOException {
		if(getChannels() == 1)
			return(__decode());
		int[] ch1 = __decode();
		int[] ch2 = this.ch2.__decode();
		int[] r = new int[ch1.length + ch2.length];
		for(int i = 0; i < ch1.length; i++) {
			r[2 * i    ] = ch1[i];
			r[2 * i + 1] = ch2[i];
		}
		return(r);
	}

	private int[] __decode() throws IOException {
		byte[] rawdata = new byte[8];
		filepos += file.read(rawdata);
		int[] samples = decoder.decode_ngc_dsp(0, 0, (int) (rawdata.length / 8.0 * 14.0), rawdata);
		current_sample += samples.length;
		if((loop_flag != 0) && ((filepos - startoffset) >= loop_end_offset)) {
			filepos = startoffset + (loop_start_offset / 8) * 8;
			seek(filepos);
		}
		return(samples);
	}

	public short[] decode16() throws Exception {
		int[] samples = doDecode();
		short[] buffer = new short[samples.length];
		for(int i = 0; i < samples.length; i++)
			buffer[i] = (short) samples[i];
		return(buffer);
	}

	public long getPreferredBufferSize() {
		return 14;
	}

	public String toString() {
		return(new String("DSP[" + sample_rate + "Hz,16bit," + sample_count + " samples,loop:" + ((loop_flag != 0) ? "yes" : "no") + "]"));
	}
}
