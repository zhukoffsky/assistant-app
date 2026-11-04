package com.zhukoffsky.magpie.core.speech

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.io.File

/** Несжатый звук: 16 бит со знаком, little-endian. */
class Pcm16(val bytes: ByteArray, val sampleRate: Int, val channels: Int)

/**
 * AAC в MP4 (то, что пишет `VoiceRecorder`) → PCM для распознавателя.
 *
 * Распознаватель на устройстве принимает звук из потока только сырым PCM,
 * сжатый файл ему не скормить. Декодирует аппаратный кодек — тот же, что
 * играет музыку, — так что это доли секунды даже на полутораминутной записи.
 */
object PcmDecoder {

    private const val TIMEOUT_US = 10_000L

    fun decode(file: File): Pcm16? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.path)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)

            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                return drain(extractor, codec, format)
            } finally {
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun drain(extractor: MediaExtractor, codec: MediaCodec, input: MediaFormat): Pcm16 {
        val out = ByteArrayOutputStream()
        val info = MediaCodec.BufferInfo()
        var sampleRate = input.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = input.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var inputDone = false
        var idleAfterInput = 0

        while (true) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val buffer = codec.getInputBuffer(inIndex)!!
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            // Страховка от кодека, который так и не отдал конец потока:
            // две секунды тишины после последнего входа — отдаём что есть.
            if (inputDone && outIndex == MediaCodec.INFO_TRY_AGAIN_LATER && ++idleAfterInput > 200) {
                return Pcm16(out.toByteArray(), sampleRate, channels)
            }
            when {
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    // Кодек вправе выдать не ту частоту, что лежит в файле:
                    // верить надо его выходному формату.
                    val format = codec.outputFormat
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
                outIndex >= 0 -> {
                    val buffer = codec.getOutputBuffer(outIndex)!!
                    val chunk = ByteArray(info.size)
                    buffer.position(info.offset)
                    buffer.get(chunk)
                    out.write(chunk)
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        return Pcm16(out.toByteArray(), sampleRate, channels)
                    }
                }
            }
        }
    }
}
