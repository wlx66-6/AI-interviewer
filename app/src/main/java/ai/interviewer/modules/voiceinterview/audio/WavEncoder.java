package ai.interviewer.modules.voiceinterview.audio;

/**
 * 把 TTS 返回的裸 PCM 拼成浏览器能直接播放的 WAV。
 *
 * <p>Qwen 实时语音接口回的是裸 PCM（24kHz / 16bit / 单声道），浏览器不认，必须补 44 字节头。
 * 这段逻辑原来埋在 1400 多行的 WebSocket handler 里，跟着连接状态一起没法单独测。
 */
public final class WavEncoder {

    private static final int HEADER_BYTES = 44;
    private static final int BITS_PER_SAMPLE = 16;
    private static final int CHANNELS = 1;
    private static final int DEFAULT_SAMPLE_RATE = 24000;

    private WavEncoder() {
    }

    public static byte[] pcmToWav(byte[] pcmData) {
        return pcmToWav(pcmData, DEFAULT_SAMPLE_RATE);
    }

    public static byte[] pcmToWav(byte[] pcmData, int sampleRate) {
        int byteRate = sampleRate * CHANNELS * BITS_PER_SAMPLE / 8;
        int blockAlign = CHANNELS * BITS_PER_SAMPLE / 8;
        int dataSize = pcmData.length;
        int fileSize = dataSize + HEADER_BYTES - 8;

        byte[] wavData = new byte[dataSize + HEADER_BYTES];
        int pos = 0;

        wavData[pos++] = 'R';
        wavData[pos++] = 'I';
        wavData[pos++] = 'F';
        wavData[pos++] = 'F';
        pos = writeIntLE(wavData, pos, fileSize);
        wavData[pos++] = 'W';
        wavData[pos++] = 'A';
        wavData[pos++] = 'V';
        wavData[pos++] = 'E';

        wavData[pos++] = 'f';
        wavData[pos++] = 'm';
        wavData[pos++] = 't';
        wavData[pos++] = ' ';
        pos = writeIntLE(wavData, pos, 16);
        pos = writeShortLE(wavData, pos, (short) 1);
        pos = writeShortLE(wavData, pos, (short) CHANNELS);
        pos = writeIntLE(wavData, pos, sampleRate);
        pos = writeIntLE(wavData, pos, byteRate);
        pos = writeShortLE(wavData, pos, (short) blockAlign);
        pos = writeShortLE(wavData, pos, (short) BITS_PER_SAMPLE);

        wavData[pos++] = 'd';
        wavData[pos++] = 'a';
        wavData[pos++] = 't';
        wavData[pos++] = 'a';
        writeIntLE(wavData, pos, dataSize);

        System.arraycopy(pcmData, 0, wavData, HEADER_BYTES, dataSize);
        return wavData;
    }

    private static int writeIntLE(byte[] buf, int pos, int value) {
        buf[pos] = (byte) (value & 0xFF);
        buf[pos + 1] = (byte) ((value >> 8) & 0xFF);
        buf[pos + 2] = (byte) ((value >> 16) & 0xFF);
        buf[pos + 3] = (byte) ((value >> 24) & 0xFF);
        return pos + 4;
    }

    private static int writeShortLE(byte[] buf, int pos, short value) {
        buf[pos] = (byte) (value & 0xFF);
        buf[pos + 1] = (byte) ((value >> 8) & 0xFF);
        return pos + 2;
    }
}
