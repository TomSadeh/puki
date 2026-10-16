// Puki's ears on the device: a small JNI wrapper around whisper.cpp (MIT) for the app's
// WhisperNative (app/src/main/java/io/github/tomsadeh/puki/brain/Ears.kt). Built by native/whisper/build.py.

#include <jni.h>
#include <string>
#include <vector>

#include "whisper.h"

// whisper.cpp's quantize example, compiled in so the app can turn the f16 model it downloads
// into the faster q8_0 on the phone (its main() is renamed out of the way)
#define main whisper_quantize_example_main
#include "quantize/quantize.cpp"
#undef main

static std::string str(JNIEnv * env, jstring s) {
    if (!s) return "";
    const char * c = env->GetStringUTFChars(s, nullptr);
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_tomsadeh_puki_brain_WhisperNative_init(JNIEnv * env, jclass, jstring model) {
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    return (jlong) whisper_init_from_file_with_params(str(env, model).c_str(), cparams);
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_tomsadeh_puki_brain_WhisperNative_free(JNIEnv *, jclass, jlong ctx) {
    if (ctx) whisper_free((whisper_context *) ctx);
}

// 16 kHz mono samples in, text out. [prompt]: words to expect (names, game words).
extern "C" JNIEXPORT jstring JNICALL
Java_io_github_tomsadeh_puki_brain_WhisperNative_transcribe(JNIEnv * env, jclass, jlong ctx, jfloatArray samples,
                                                  jstring lang, jstring prompt, jint audio_ctx, jint threads) {
    std::string language = str(env, lang);
    std::string initial = str(env, prompt);
    whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.language = language.c_str();
    p.translate = false;
    p.no_timestamps = true;
    p.single_segment = true;
    p.no_context = true;
    p.print_progress = false;
    p.print_realtime = false;
    p.print_special = false;
    p.print_timestamps = false;
    p.suppress_blank = true;
    p.audio_ctx = audio_ctx;  // a shorter window: much less work for a word or two
    p.n_threads = threads;
    p.initial_prompt = initial.empty() ? nullptr : initial.c_str();

    jsize n = env->GetArrayLength(samples);
    std::vector<float> pcm(n);
    env->GetFloatArrayRegion(samples, 0, n, pcm.data());
    std::string text;
    if (whisper_full((whisper_context *) ctx, p, pcm.data(), n) == 0) {
        const int segments = whisper_full_n_segments((whisper_context *) ctx);
        for (int i = 0; i < segments; i++) text += whisper_full_get_segment_text((whisper_context *) ctx, i);
    }
    return env->NewStringUTF(text.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_tomsadeh_puki_brain_WhisperNative_quantizeQ8(JNIEnv * env, jclass, jstring in, jstring out) {
    struct ggml_init_params params = { 0, NULL, false };  // initializes ggml's f16 tables
    ggml_free(ggml_init(params));
    return whisper_model_quantize(str(env, in), str(env, out), GGML_FTYPE_MOSTLY_Q8_0) ? JNI_TRUE : JNI_FALSE;
}
