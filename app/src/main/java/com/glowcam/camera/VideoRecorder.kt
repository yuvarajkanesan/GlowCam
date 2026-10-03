package com.glowcam.camera

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Surface

/**
 * H.264 + AAC recorder. Video frames come from the GL pipeline through [inputSurface], so beauty
 * effects and filters are baked into the file.
 */
class VideoRecorder(
    private val context: Context,
    private val width: Int,
    private val height: Int,
    private var withAudio: Boolean,
    private val fps: Int = 30,
) {
    val t0: Long = System.nanoTime()

    private val uri: Uri = MediaSaver.createVideo(context)
    private var pfd: ParcelFileDescriptor? = null
    private var muxer: MediaMuxer? = null
    private var videoEnc: MediaCodec? = null
    private var audioEnc: MediaCodec? = null
    private var audioRec: AudioRecord? = null
    private var videoThread: Thread? = null
    private var audioThread: Thread? = null

    private val lock = Object()
    private var videoTrack = -1
    private var audioTrack = -1
    private var muxerStarted = false
    private val lastPts = longArrayOf(0L, 0L)

    @Volatile private var stopping = false

    /** Starts the encoders and returns the surface the GL thread must render into. */
    fun start(): Surface {
        pfd = context.contentResolver.openFileDescriptor(uri, "w")
        muxer = MediaMuxer(pfd!!.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val vf = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            val cap = if (width.toLong() * height > 4_000_000L) 45_000_000 else 16_000_000
            setInteger(MediaFormat.KEY_BIT_RATE, (width.toLong() * height * 6 * fps / 30).coerceAtMost(cap.toLong()).toInt())
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val venc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        venc.configure(vf, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = venc.createInputSurface()
        venc.start()
        videoEnc = venc

        if (withAudio) {
            try {
                setupAudio()
            } catch (e: Exception) {
                Log.w(TAG, "Audio unavailable, recording silent video", e)
                withAudio = false
                audioRec?.release(); audioRec = null
                audioEnc?.release(); audioEnc = null
            }
        }

        videoThread = Thread({ videoLoop(venc) }, "glow-venc").also { it.start() }
        audioThread = if (withAudio) Thread({ audioLoop() }, "glow-aenc").also { it.start() } else null
        return surface
    }

    @Suppress("MissingPermission")
    private fun setupAudio() {
        val rate = 44100
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, minBuf * 2,
        )
        check(rec.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord not initialised" }
        val af = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 96_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val aenc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        aenc.configure(af, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        aenc.start()
        audioRec = rec
        audioEnc = aenc
    }

    private fun videoLoop(enc: MediaCodec) {
        try {
            var eos = false
            while (!eos) eos = drain(enc, isVideo = true)
        } catch (e: Exception) {
            Log.e(TAG, "video loop", e)
        }
    }

    private fun audioLoop() {
        val rec = audioRec ?: return
        val enc = audioEnc ?: return
        try {
            rec.startRecording()
            val buf = ByteArray(4096)
            while (!stopping) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) feedAudio(enc, buf, n, eos = false)
                drain(enc, isVideo = false)
            }
            rec.stop()
            feedAudio(enc, buf, 0, eos = true)
            var eos = false
            while (!eos) eos = drain(enc, isVideo = false)
        } catch (e: Exception) {
            Log.e(TAG, "audio loop", e)
        }
    }

    private fun feedAudio(enc: MediaCodec, data: ByteArray, n: Int, eos: Boolean) {
        val idx = enc.dequeueInputBuffer(10_000)
        if (idx < 0) return
        val ib = enc.getInputBuffer(idx)!!
        ib.clear()
        ib.put(data, 0, n)
        val ptsUs = (System.nanoTime() - t0) / 1000 - (n / 2) * 1_000_000L / 44100
        enc.queueInputBuffer(idx, 0, n, ptsUs.coerceAtLeast(0), if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
    }

    /** Pulls available output into the muxer. Returns true once end-of-stream was seen. */
    private fun drain(enc: MediaCodec, isVideo: Boolean): Boolean {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val idx = enc.dequeueOutputBuffer(info, 10_000)
            when {
                idx == MediaCodec.INFO_TRY_AGAIN_LATER -> return false
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    synchronized(lock) {
                        val track = muxer!!.addTrack(enc.outputFormat)
                        if (isVideo) videoTrack = track else audioTrack = track
                        if (videoTrack >= 0 && (!withAudio || audioTrack >= 0) && !muxerStarted) {
                            muxer!!.start(); muxerStarted = true
                        }
                    }
                }
                idx >= 0 -> {
                    val out = enc.getOutputBuffer(idx)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0) {
                        val slot = if (isVideo) 0 else 1
                        synchronized(lock) {
                            if (muxerStarted) {
                                if (info.presentationTimeUs <= lastPts[slot]) info.presentationTimeUs = lastPts[slot] + 1
                                lastPts[slot] = info.presentationTimeUs
                                out.position(info.offset); out.limit(info.offset + info.size)
                                muxer!!.writeSampleData(if (isVideo) videoTrack else audioTrack, out, info)
                            }
                        }
                    }
                    enc.releaseOutputBuffer(idx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return true
                }
            }
        }
    }

    /** Call after the GL thread stopped drawing into the encoder surface. Blocks until finished. */
    fun stop(): Uri? {
        stopping = true
        try { videoEnc?.signalEndOfInputStream() } catch (_: Exception) {}
        videoThread?.join(3000)
        audioThread?.join(3000)
        val hadData = synchronized(lock) { muxerStarted }
        try { if (hadData) muxer?.stop() } catch (e: Exception) { Log.w(TAG, "muxer stop", e) }
        runCatching { muxer?.release() }
        runCatching { videoEnc?.stop(); videoEnc?.release() }
        runCatching { audioEnc?.stop(); audioEnc?.release() }
        runCatching { audioRec?.release() }
        runCatching { pfd?.close() }
        return if (hadData) {
            MediaSaver.finishVideo(context, uri)
            uri
        } else {
            MediaSaver.delete(context, uri)
            null
        }
    }

    private companion object {
        const val TAG = "VideoRecorder"
    }
}
