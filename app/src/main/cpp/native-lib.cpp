#include <jni.h>
#include <string>
#include <oboe/Oboe.h>
#include <android/log.h>
#include <vector>
#include <mutex>
#include <algorithm>
#include <thread>
#include <chrono>
#include <atomic>
#include <cmath>

#define LOG_TAG "NativeAudioPlayer"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

const size_t MAX_BUFFER_SAMPLES = 1024 * 1024; // ~1M samples (~10s of audio)

class NativeAudioPlayer : public oboe::AudioStreamDataCallback {
public:
    NativeAudioPlayer() {
        mAudioBuffer.resize(MAX_BUFFER_SAMPLES, 0.0f);
        reset();
        createStream(44100);
    }

    void createStream(int32_t sampleRate) {
        std::lock_guard<std::mutex> lock(mStreamMutex);
        if (mStream) {
            mStream->stop();
            mStream->close();
        }

        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output)
               ->setFormat(oboe::AudioFormat::Float) // Let system mixer handle conversion
               ->setChannelCount(oboe::ChannelCount::Stereo)
               ->setSampleRate(sampleRate)
               ->setPerformanceMode(oboe::PerformanceMode::None) // Highest compatibility for Android 7
               ->setSharingMode(oboe::SharingMode::Shared)
               ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
               ->setDataCallback(this);

        oboe::Result result = builder.openStream(mStream);
        if (result != oboe::Result::OK) {
            LOGE("Error opening stream: %s", oboe::convertToText(result));
        }

        // Use a conservative buffer size for Android 7 stability
        if (mStream) {
            mStream->setBufferSizeInFrames(mStream->getFramesPerBurst() * 4);
            mActualSampleRate.store(mStream->getSampleRate());
        }
    }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *oboeStream, void *audioData, int32_t numFrames) override {
        float *output = static_cast<float *>(audioData);

        size_t readIdx = mReadIndex.load(std::memory_order_acquire);
        size_t writeIdx = mWriteIndex.load(std::memory_order_acquire);

        int32_t samplesToRead = numFrames * 2;
        int32_t samplesAvailable = (writeIdx >= readIdx) ? (writeIdx - readIdx) : (MAX_BUFFER_SAMPLES - readIdx + writeIdx);

        if (!mIsRunning.load()) {
            std::fill(output, output + samplesToRead, 0.0f);
            return oboe::DataCallbackResult::Continue;
        }

        int32_t actualRead = std::min(samplesToRead, samplesAvailable);

        for (int32_t i = 0; i < actualRead; ++i) {
            float x = mAudioBuffer[readIdx];

            // --- ANTI-CLIPPING ---
            // On legacy DACs, we need even more headroom.
            // Using a Tanh soft clipper with a very gentle slope.
            float absX = std::abs(x);
            if (absX > 0.4f) {
                float excess = absX - 0.4f;
                float compressed = 0.4f + (0.5f * std::tanh(excess / 0.5f));
                x = (x > 0) ? compressed : -compressed;
            }

            output[i] = x;
            readIdx = (readIdx + 1) % MAX_BUFFER_SAMPLES;
        }

        if (actualRead < samplesToRead) {
            std::fill(output + actualRead, output + samplesToRead, 0.0f);
        }

        mReadIndex.store(readIdx, std::memory_order_release);
        mTotalSamplesPlayed.fetch_add(actualRead / 2, std::memory_order_relaxed);

        return oboe::DataCallbackResult::Continue;
    }

    void enqueueData(const float *data, int32_t numSamples) {
        size_t writeIdx = mWriteIndex.load(std::memory_order_acquire);

        for (int32_t i = 0; i < numSamples; ++i) {
            size_t nextWriteIdx = (writeIdx + 1) % MAX_BUFFER_SAMPLES;

            // Use a shorter wait for better responsiveness on old Android
            while (nextWriteIdx == mReadIndex.load(std::memory_order_acquire) && mIsRunning.load()) {
                std::this_thread::sleep_for(std::chrono::milliseconds(1));
            }

            if (!mIsRunning.load()) return;

            mAudioBuffer[writeIdx] = data[i];
            writeIdx = nextWriteIdx;
        }
        mWriteIndex.store(writeIdx, std::memory_order_release);
    }

    int64_t getPlaybackPositionMs() {
        std::lock_guard<std::mutex> lock(mPositionMutex);
        int32_t rate = mActualSampleRate.load();
        if (rate <= 0) return mPositionBaseMs;
        return mPositionBaseMs + (mTotalSamplesPlayed.load() * 1000) / rate;
    }

    void setPositionBase(int64_t baseMs) {
        std::lock_guard<std::mutex> lock(mPositionMutex);
        mPositionBaseMs = baseMs;
        mTotalSamplesPlayed.store(0);
    }

    void start() {
        mIsRunning.store(true);
        std::lock_guard<std::mutex> lock(mStreamMutex);
        if (mStream) {
            oboe::Result result = mStream->requestStart();
            if (result != oboe::Result::OK) LOGE("Error starting stream: %s", oboe::convertToText(result));
        }
    }

    void stop() {
        mIsRunning.store(false);
        std::lock_guard<std::mutex> lock(mStreamMutex);
        if (mStream) mStream->requestStop();
    }

    void flush() {
        mReadIndex.store(mWriteIndex.load());
    }

    void reset() {
        std::lock_guard<std::mutex> lock(mPositionMutex);
        mReadIndex.store(0);
        mWriteIndex.store(0);
        mTotalSamplesPlayed.store(0);
        mPositionBaseMs = 0;
    }

private:
    std::shared_ptr<oboe::AudioStream> mStream;
    std::mutex mStreamMutex;
    std::mutex mPositionMutex;

    std::vector<float> mAudioBuffer;
    std::atomic<size_t> mReadIndex{0};
    std::atomic<size_t> mWriteIndex{0};

    std::atomic<int64_t> mTotalSamplesPlayed{0};
    int64_t mPositionBaseMs{0};
    std::atomic<int32_t> mActualSampleRate{44100};
    std::atomic<bool> mIsRunning{false};
};

static NativeAudioPlayer *gPlayer = nullptr;

extern "C" JNIEXPORT void JNICALL
Java_com_endfield_pearmusic_playback_NativeAudioPlayer_nativeInit(JNIEnv *env, jobject thiz) {
    if (gPlayer == nullptr) {
        gPlayer = new NativeAudioPlayer();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_endfield_pearmusic_playback_NativeAudioPlayer_nativeStart(JNIEnv *env, jobject thiz) {
    if (gPlayer) gPlayer->start();
}

extern "C" JNIEXPORT void JNICALL
Java_com_endfield_pearmusic_playback_NativeAudioPlayer_nativeStop(JNIEnv *env, jobject thiz) {
    if (gPlayer) gPlayer->stop();
}

extern "C" JNIEXPORT void JNICALL
Java_com_endfield_pearmusic_playback_NativeAudioPlayer_nativeEnqueueData(JNIEnv *env, jobject thiz, jfloatArray data, jint numSamples) {
    if (gPlayer) {
        jfloat *samples = env->GetFloatArrayElements(data, nullptr);
        gPlayer->enqueueData(samples, numSamples);
        env->ReleaseFloatArrayElements(data, samples, JNI_ABORT);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_endfield_pearmusic_playback_NativeAudioPlayer_nativeFlush(JNIEnv *env, jobject thiz) {
    if (gPlayer) gPlayer->flush();
}

extern "C" JNIEXPORT void JNICALL
Java_com_endfield_pearmusic_playback_NativeAudioPlayer_nativeReset(JNIEnv *env, jobject thiz) {
    if (gPlayer) gPlayer->reset();
}

extern "C" JNIEXPORT void JNICALL
Java_com_endfield_pearmusic_playback_NativeAudioPlayer_nativeSetSampleRate(JNIEnv *env, jobject thiz, jint sample_rate) {
    if (gPlayer) gPlayer->createStream(sample_rate);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_endfield_pearmusic_playback_NativeAudioPlayer_nativeGetPosition(JNIEnv *env, jobject thiz) {
    if (gPlayer) return gPlayer->getPlaybackPositionMs();
    return 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_endfield_pearmusic_playback_NativeAudioPlayer_nativeSetPositionBase(JNIEnv *env, jobject thiz, jlong base_ms) {
    if (gPlayer) gPlayer->setPositionBase(base_ms);
}
