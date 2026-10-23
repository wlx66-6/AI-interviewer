package ai.interviewer.modules.voiceinterview.audio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WAV 头里的每个字段都直接决定浏览器放不放得出来，所以这里逐字段断言，而不是只看长度。
 */
@DisplayName("PCM 转 WAV")
class WavEncoderTest {

    private static String ascii(byte[] data, int offset, int length) {
        return new String(data, offset, length, StandardCharsets.US_ASCII);
    }

    private static int intAt(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static short shortAt(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
    }

    @Test
    @DisplayName("输出是合法的 RIFF/WAVE 结构")
    void writesRiffStructure() {
        byte[] pcm = new byte[] {1, 2, 3, 4};

        byte[] wav = WavEncoder.pcmToWav(pcm);

        assertThat(wav).hasSize(48);
        assertThat(ascii(wav, 0, 4)).isEqualTo("RIFF");
        assertThat(ascii(wav, 8, 4)).isEqualTo("WAVE");
        assertThat(ascii(wav, 12, 4)).isEqualTo("fmt ");
        assertThat(ascii(wav, 36, 4)).isEqualTo("data");
    }

    @Test
    @DisplayName("RIFF 长度与 data 长度都算上了自己的头")
    void writesSizes() {
        byte[] pcm = new byte[1000];

        byte[] wav = WavEncoder.pcmToWav(pcm);

        assertThat(intAt(wav, 4)).isEqualTo(1036);
        assertThat(intAt(wav, 40)).isEqualTo(1000);
    }

    @Test
    @DisplayName("默认按 24kHz 单声道 16bit 写格式块")
    void writesFormatBlock() {
        byte[] wav = WavEncoder.pcmToWav(new byte[10]);

        assertThat(intAt(wav, 16)).isEqualTo(16);
        assertThat(shortAt(wav, 20)).isEqualTo((short) 1);
        assertThat(shortAt(wav, 22)).isEqualTo((short) 1);
        assertThat(intAt(wav, 24)).isEqualTo(24000);
        assertThat(intAt(wav, 28)).isEqualTo(48000);
        assertThat(shortAt(wav, 32)).isEqualTo((short) 2);
        assertThat(shortAt(wav, 34)).isEqualTo((short) 16);
    }

    @Test
    @DisplayName("采样率可覆盖，字节率跟着变")
    void honoursRequestedSampleRate() {
        byte[] wav = WavEncoder.pcmToWav(new byte[10], 16000);

        assertThat(intAt(wav, 24)).isEqualTo(16000);
        assertThat(intAt(wav, 28)).isEqualTo(32000);
    }

    @Test
    @DisplayName("PCM 原样接在头后面")
    void copiesPayload() {
        byte[] pcm = new byte[] {(byte) 0x7F, (byte) 0x80, 0x00};

        byte[] wav = WavEncoder.pcmToWav(pcm);

        assertThat(wav).hasSize(47);
        assertThat(new byte[] {wav[44], wav[45], wav[46]}).containsExactly(pcm);
    }

    @Test
    @DisplayName("空负载也产出只有头的合法文件")
    void handlesEmptyPayload() {
        byte[] wav = WavEncoder.pcmToWav(new byte[0]);

        assertThat(wav).hasSize(44);
        assertThat(intAt(wav, 40)).isZero();
    }
}
