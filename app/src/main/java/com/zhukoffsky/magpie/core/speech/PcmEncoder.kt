package com.zhukoffsky.magpie.core.speech

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import com.zhukoffsky.magpie.core.util.MagpieLog
import java.io.File

/**
 * PCM → AAC в MP4, для Whisper.
 *
 * Запись теперь сырая — её слушает распознаватель на телефоне, — а в облако
 * уходит сжатой: AAC в 32 кбит/с в десять раз легче PCM, и на мобильной сети
 * это секунды. Формат тот же, что раньше писал `MediaRecorder`, и он
 * проверен сквозным запросом к Whisper (раздел 3 `CLAUDE.md`).
 */
object PcmEncoder {

    private const val MIME = MediaFormat.MIMETYPE_AUDIO_AAC
    private const val BIT_RATE = 32_000
    private const val TIMEOUT_US = 10_000L

    fun encodeAac(pcm: Pcm16): ByteArray? = runCatching { encode(pcm) }
        .onFailure { MagpieLog.w("encoder: aac failed", it) }
        .getOrNull()

    private fun encode(pcm: Pcm16): ByteArray {
        val file = File.createTempFile("dictation", ".m4a")
        val format = MediaFormat.createAudioFormat(MIME, pcm.sampleRate, pcm.channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val codec = MediaCodec.createEncoderByType(MIME)
        val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val bytesPerSecond = pcm.sampleRate * pcm.channels * 2
            val info = MediaCodec.BufferInfo()
            var offset = 0
            var inputDone = false
            var track = -1

            while (true) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        val size = minOf(buffer.capacity(), pcm.bytes.size - offset)
                        val timeUs = offset * 1_000_000L / bytesPerSecond
                        if (size <= 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, timeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buffer.clear()
                            buffer.put(pcm.bytes, offset, size)
                            codec.queueInputBuffer(inIndex, 0, size, timeUs, 0)
                            offset += size
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                    }
                    outIndex >= 0 -> {
                        val buffer = codec.getOutputBuffer(outIndex)!!
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (!isConfig && info.size > 0 && track >= 0) {
                            muxer.writeSampleData(track, buffer, info)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
            codec.stop()
            if (track >= 0) muxer.stop()
        } finally {
            codec.release()
            runCatching { muxer.release() }
        }
        return file.readBytes().also { file.delete() }
    }
}
