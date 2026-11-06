// Frontend libretro minimal: memuat core mGBA (libmgba_core.so) lewat dlopen.
#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>
#include <atomic>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <algorithm>
#include <string>
#include <vector>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "FF6", __VA_ARGS__)

struct retro_game_info { const char *path; const void *data; size_t size; const char *meta; };
struct retro_system_av_info {
    struct { unsigned base_width, base_height, max_width, max_height; float aspect_ratio; } geometry;
    struct { double fps, sample_rate; } timing;
};

typedef bool (*env_t)(unsigned, void *);
typedef void (*video_t)(const void *, unsigned, unsigned, size_t);
typedef void (*audio_t)(int16_t, int16_t);
typedef size_t (*audio_batch_t)(const int16_t *, size_t);
typedef void (*poll_t)(void);
typedef int16_t (*input_t)(unsigned, unsigned, unsigned, unsigned);

static void *g_lib = nullptr;
static void (*r_init)(void);
static void (*r_deinit)(void);
static void (*r_set_env)(env_t);
static void (*r_set_video)(video_t);
static void (*r_set_audio)(audio_t);
static void (*r_set_audio_batch)(audio_batch_t);
static void (*r_set_poll)(poll_t);
static void (*r_set_input)(input_t);
static bool (*r_load)(const retro_game_info *);
static void (*r_unload)(void);
static void (*r_run)(void);
static void (*r_av_info)(retro_system_av_info *);
static void *(*r_mem_data)(unsigned);
static size_t (*r_mem_size)(unsigned);

static std::string g_sys_dir, g_rom_path;
static std::vector<uint8_t> g_rom;
static std::vector<uint16_t> g_fb;
static unsigned g_w = 0, g_h = 0;
static std::vector<int16_t> g_audio;
static std::atomic<uint32_t> g_pad{0};
static double g_rate = 32768.0, g_fps = 60.0;

static bool env_cb(unsigned cmd, void *data) {
    switch (cmd) {
        case 3:  // GET_CAN_DUPE
            *(bool *)data = true; return true;
        case 9:  // GET_SYSTEM_DIRECTORY
        case 31: // GET_SAVE_DIRECTORY
            *(const char **)data = g_sys_dir.c_str(); return true;
        case 10: // SET_PIXEL_FORMAT
            return *(int *)data == 2; // hanya RGB565
        default: return false;
    }
}

static void video_cb(const void *data, unsigned w, unsigned h, size_t pitch) {
    if (!data) return;
    g_fb.resize((size_t)w * h);
    for (unsigned y = 0; y < h; y++)
        memcpy(&g_fb[(size_t)y * w], (const uint8_t *)data + y * pitch, (size_t)w * 2);
    g_w = w; g_h = h;
}
static void audio_cb(int16_t l, int16_t r) { g_audio.push_back(l); g_audio.push_back(r); }
static size_t audio_batch_cb(const int16_t *d, size_t frames) {
    g_audio.insert(g_audio.end(), d, d + frames * 2);
    return frames;
}
static void poll_cb(void) {}
static int16_t input_cb(unsigned port, unsigned device, unsigned, unsigned id) {
    if (port != 0 || device != 1) return 0;
    uint32_t m = g_pad.load();
    if (id == 256) return (int16_t)m;
    return id < 16 ? (int16_t)((m >> id) & 1) : 0;
}

static std::string jstr(JNIEnv *env, jstring s) {
    const char *c = env->GetStringUTFChars(s, nullptr);
    std::string r = c ? c : "";
    env->ReleaseStringUTFChars(s, c);
    return r;
}

#define SYM(var, name) *(void **)(&var) = dlsym(g_lib, name)

extern "C" {

JNIEXPORT jboolean JNICALL Java_com_ff6advance_app_Native_start(JNIEnv *env, jobject, jstring rom, jstring sys) {
    g_rom_path = jstr(env, rom);
    g_sys_dir = jstr(env, sys);
    g_lib = dlopen("libmgba_core.so", RTLD_NOW);
    if (!g_lib) { LOGI("dlopen gagal: %s", dlerror()); return JNI_FALSE; }
    SYM(r_init, "retro_init"); SYM(r_deinit, "retro_deinit");
    SYM(r_set_env, "retro_set_environment"); SYM(r_set_video, "retro_set_video_refresh");
    SYM(r_set_audio, "retro_set_audio_sample"); SYM(r_set_audio_batch, "retro_set_audio_sample_batch");
    SYM(r_set_poll, "retro_set_input_poll"); SYM(r_set_input, "retro_set_input_state");
    SYM(r_load, "retro_load_game"); SYM(r_unload, "retro_unload_game"); SYM(r_run, "retro_run");
    SYM(r_av_info, "retro_get_system_av_info");
    SYM(r_mem_data, "retro_get_memory_data"); SYM(r_mem_size, "retro_get_memory_size");
    if (!r_init || !r_set_env || !r_load || !r_run || !r_av_info) { LOGI("simbol core kurang"); return JNI_FALSE; }

    r_set_env(env_cb);
    r_set_video(video_cb); r_set_audio(audio_cb); r_set_audio_batch(audio_batch_cb);
    r_set_poll(poll_cb); r_set_input(input_cb);
    r_init();

    FILE *f = fopen(g_rom_path.c_str(), "rb");
    if (!f) { LOGI("ROM tidak bisa dibuka"); return JNI_FALSE; }
    fseek(f, 0, SEEK_END); long n = ftell(f); fseek(f, 0, SEEK_SET);
    g_rom.resize(n > 0 ? (size_t)n : 0);
    size_t got = fread(g_rom.data(), 1, g_rom.size(), f);
    fclose(f);
    if (got != g_rom.size() || g_rom.empty()) return JNI_FALSE;

    retro_game_info gi{g_rom_path.c_str(), g_rom.data(), g_rom.size(), nullptr};
    if (!r_load(&gi)) { LOGI("retro_load_game gagal"); return JNI_FALSE; }
    retro_system_av_info av{};
    r_av_info(&av);
    if (av.timing.sample_rate > 0) g_rate = av.timing.sample_rate;
    if (av.timing.fps > 0) g_fps = av.timing.fps;
    return JNI_TRUE;
}

JNIEXPORT jdoubleArray JNICALL Java_com_ff6advance_app_Native_info(JNIEnv *env, jobject) {
    jdoubleArray a = env->NewDoubleArray(2);
    double v[2] = {g_rate, g_fps};
    env->SetDoubleArrayRegion(a, 0, 2, v);
    return a;
}

JNIEXPORT void JNICALL Java_com_ff6advance_app_Native_runFrame(JNIEnv *, jobject) {
    g_audio.clear();
    r_run();
}

JNIEXPORT jint JNICALL Java_com_ff6advance_app_Native_frame(JNIEnv *env, jobject, jshortArray out) {
    if (!g_w || !g_h) return 0;
    jsize cap = env->GetArrayLength(out);
    jsize n = (jsize)std::min<size_t>((size_t)cap, g_fb.size());
    env->SetShortArrayRegion(out, 0, n, (const jshort *)g_fb.data());
    return (jint)((g_w << 16) | g_h);
}

JNIEXPORT jint JNICALL Java_com_ff6advance_app_Native_audio(JNIEnv *env, jobject, jshortArray out) {
    jsize cap = env->GetArrayLength(out);
    jsize n = (jsize)std::min<size_t>((size_t)cap, g_audio.size());
    env->SetShortArrayRegion(out, 0, n, (const jshort *)g_audio.data());
    return n;
}

JNIEXPORT void JNICALL Java_com_ff6advance_app_Native_setPad(JNIEnv *, jobject, jint mask) {
    g_pad.store((uint32_t)mask);
}

JNIEXPORT jboolean JNICALL Java_com_ff6advance_app_Native_loadSram(JNIEnv *env, jobject, jstring path) {
    if (!r_mem_data || !r_mem_size) return JNI_FALSE;
    void *p = r_mem_data(0); size_t sz = r_mem_size(0);
    if (!p || !sz) return JNI_FALSE;
    FILE *f = fopen(jstr(env, path).c_str(), "rb");
    if (!f) return JNI_FALSE;
    size_t got = fread(p, 1, sz, f);
    fclose(f);
    return got > 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL Java_com_ff6advance_app_Native_saveSram(JNIEnv *env, jobject, jstring path) {
    if (!r_mem_data || !r_mem_size) return JNI_FALSE;
    void *p = r_mem_data(0); size_t sz = r_mem_size(0);
    if (!p || !sz) return JNI_FALSE;
    std::string tmp = jstr(env, path) + ".tmp", dst = jstr(env, path);
    FILE *f = fopen(tmp.c_str(), "wb");
    if (!f) return JNI_FALSE;
    size_t w = fwrite(p, 1, sz, f);
    fclose(f);
    if (w != sz) return JNI_FALSE;
    return rename(tmp.c_str(), dst.c_str()) == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_ff6advance_app_Native_stop(JNIEnv *, jobject) {
    if (r_unload) r_unload();
    if (r_deinit) r_deinit();
    if (g_lib) { dlclose(g_lib); g_lib = nullptr; }
    g_rom.clear(); g_fb.clear(); g_audio.clear(); g_w = g_h = 0;
}

} // extern "C"
